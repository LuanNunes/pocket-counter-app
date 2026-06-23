package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
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
        val newTokenResponse = TokenResponse(
            accessToken = "new-access-token",
            refreshToken = "new-refresh-token",
            expiresIn = 3600L,
        )
        coEvery { tokenStore.getRefreshToken() } returns "old-refresh-token"
        coEvery { authApi.refresh(any()) } returns retrofit2.Response.success(newTokenResponse)

        val response = buildResponse("/api/v1/notifications/pending")

        val result = authenticator.authenticate(null, response)

        assertNotNull(result)
        assert(result!!.header("Authorization") == "Bearer new-access-token")
        coVerify { authApi.refresh(any()) }
    }

    @Test
    fun `authenticate returns null without calling refresh when refresh token is absent`() {
        coEvery { tokenStore.getRefreshToken() } returns null

        val response = buildResponse("/api/v1/transactions/expenses")

        val result = authenticator.authenticate(null, response)

        assertNull(result)
        coVerify(exactly = 0) { authApi.refresh(any()) }
    }
}
