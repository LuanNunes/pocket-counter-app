package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.IOException
import javax.inject.Provider

class TokenAuthenticatorTest {

    private val tokenStore = mockk<TokenStore>(relaxed = true)
    private val authApi = mockk<AuthApi>()
    private val authApiProvider = Provider { authApi }

    private lateinit var authenticator: TokenAuthenticator

    @Before
    fun setUp() {
        authenticator = TokenAuthenticator(tokenStore, authApiProvider)
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    /** Builds a 401 response for the given path, with no Authorization header on the request. */
    private fun buildResponse(urlPath: String, code: Int = 401): Response {
        val request = Request.Builder()
            .url("http://10.0.2.2:8080$urlPath")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Unauthorized")
            .build()
    }

    /** Builds a 401 response whose originating request carries a stale bearer token. */
    private fun buildResponseWithBearer(
        urlPath: String,
        staleBearer: String,
        code: Int = 401,
    ): Response {
        val request = Request.Builder()
            .url("http://10.0.2.2:8080$urlPath")
            .header("Authorization", "Bearer $staleBearer")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Unauthorized")
            .build()
    }

    /**
     * Builds a 401 response that already has one prior response, so responseCount == 2.
     * OkHttp counts the chain as: prior (1) + current (1) = 2.
     */
    private fun buildResponseWithPrior(urlPath: String): Response {
        val request = Request.Builder()
            .url("http://10.0.2.2:8080$urlPath")
            .header("Authorization", "Bearer stale-token")
            .build()
        val prior = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .priorResponse(prior)
            .build()
    }

    private fun successTokenResponse(
        accessToken: String = "new-access-token",
        refreshToken: String = "new-refresh-token",
    ) = TokenResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = 3600L,
    )

    // ─── pre-existing tests (kept, wording preserved) ─────────────────────────

    @Test
    fun `authenticate returns null without calling refresh when 401 comes from an auth path`() {
        val response = buildResponse("/api/v1/auth/refresh")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
        coVerify { tokenStore.clear() }
    }

    @Test
    fun `authenticate triggers a refresh and returns a request with new bearer token for a non-auth 401`() {
        coEvery { tokenStore.getRefreshToken() } returns "old-refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.success(successTokenResponse())

        val response = buildResponse("/api/v1/notifications/pending")

        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer new-access-token", result!!.header("Authorization"))
        coVerify(exactly = 1) { authApi.refresh(any()) }
    }

    @Test
    fun `authenticate returns null without calling refresh when refresh token is absent`() {
        coEvery { tokenStore.getRefreshToken() } returns null

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
    }

    // ─── responseCount guard ──────────────────────────────────────────────────

    @Test
    fun `authenticate returns null and does not refresh when responseCount is 2 or more`() {
        val response = buildResponseWithPrior("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
        coVerify(exactly = 0) { tokenStore.clear() }
    }

    // ─── already-fresh double-check ───────────────────────────────────────────

    /**
     * When getAccessToken() returns a token that is DIFFERENT from the stale bearer on
     * the failed request, another coroutine has already refreshed.  The "loser" must
     * skip the network call and return the rotated token directly.
     */
    @Test
    fun `authenticate skips refresh and returns rotated token when access token already differs from failed bearer`() {
        val staleBEARER = "stale-token"
        val rotatedToken = "already-rotated-access-token"

        coEvery { tokenStore.getAccessToken() } returns rotatedToken
        coEvery { tokenStore.getRefreshToken() } returns "some-refresh-token"

        val response = buildResponseWithBearer("/api/v1/transactions/expenses", staleBEARER)

        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assertEquals("Bearer $rotatedToken", result!!.header("Authorization"))
        coVerify(exactly = 0) { authApi.refresh(any()) }
    }

    /**
     * When failedBearer is null (no Authorization header was on the original request),
     * we must NOT treat the access token as already-fresh — proceed to refresh.
     */
    @Test
    fun `authenticate proceeds to refresh when failed request carries no Authorization header`() {
        coEvery { tokenStore.getAccessToken() } returns "some-current-token"
        coEvery { tokenStore.getRefreshToken() } returns "some-refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.success(successTokenResponse())

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        coVerify(exactly = 1) { authApi.refresh(any()) }
    }

    // ─── refresh response code differentiation ────────────────────────────────

    @Test
    fun `authenticate clears session and returns null when refresh returns 401`() {
        coEvery { tokenStore.getRefreshToken() } returns "refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.error(
            401,
            "".toResponseBody(),
        )

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify { tokenStore.clear() }
    }

    @Test
    fun `authenticate clears session and returns null when refresh returns 403`() {
        coEvery { tokenStore.getRefreshToken() } returns "refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.error(
            403,
            "".toResponseBody(),
        )

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify { tokenStore.clear() }
    }

    @Test
    fun `authenticate does NOT clear session and returns null when refresh returns 500`() {
        coEvery { tokenStore.getRefreshToken() } returns "refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.error(
            500,
            "".toResponseBody(),
        )

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { tokenStore.clear() }
    }

    @Test
    fun `authenticate does NOT clear session and returns null when refresh throws IOException`() {
        coEvery { tokenStore.getRefreshToken() } returns "refresh-token"
        coEvery { authApi.refresh(any()) } throws IOException("network failure")

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { tokenStore.clear() }
    }

    // ─── serialization / concurrency ─────────────────────────────────────────

    /**
     * Simulates the race: N coroutines all fail with the same stale bearer.
     * After the mutex serializes them, the first to acquire the lock refreshes;
     * subsequent ones see getAccessToken() != staleBEARER and skip the network call.
     *
     * We test the invariant mechanically: stub getAccessToken() to return the
     * stale token on the first call and the rotated token on all subsequent calls,
     * then run two concurrent authenticate calls and assert refresh was called
     * exactly once.
     */
    @Test
    fun `concurrent 401s result in exactly one refresh call`() = runTest {
        val staleBearer = "stale-token"
        val newAccess = "new-access-token"
        val newRefresh = "new-refresh-token"

        // First getAccessToken() call (inside the lock for the winner) returns the stale token,
        // triggering the refresh.  All subsequent calls return the new token, causing losers
        // to short-circuit.
        var accessTokenCallCount = 0
        coEvery { tokenStore.getAccessToken() } answers {
            accessTokenCallCount++
            if (accessTokenCallCount == 1) staleBearer else newAccess
        }
        coEvery { tokenStore.getRefreshToken() } returns "refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.success(
            successTokenResponse(accessToken = newAccess, refreshToken = newRefresh),
        )

        val response1 = buildResponseWithBearer("/api/v1/transactions/expenses", staleBearer)
        val response2 = buildResponseWithBearer("/api/v1/transactions/expenses", staleBearer)

        var result1: Request? = null
        var result2: Request? = null

        val job1 = launch { result1 = authenticator.authenticate(null, response1) }
        val job2 = launch { result2 = authenticator.authenticate(null, response2) }
        job1.join()
        job2.join()

        coVerify(exactly = 1) { authApi.refresh(any()) }
        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals("Bearer $newAccess", result1!!.header("Authorization"))
        assertEquals("Bearer $newAccess", result2!!.header("Authorization"))
    }
}
