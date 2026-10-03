package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.LoginRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for AuthRepository ensuring the AppLockState integration:
 *
 *   successful login → appLockState.unlock() called (isUnlocked becomes true)
 *   failed login (non-2xx) → appLockState.unlock() NOT called (isUnlocked stays false)
 *   network exception → appLockState.unlock() NOT called
 */
class AuthRepositoryTest {

    private val authApi: AuthApi = mockk()
    private val tokenStore: TokenStore = mockk(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val appLockState = AppLockState()

    private fun makeRepo() = AuthRepository(authApi, tokenStore, json, appLockState)

    private fun successResponse() = Response.success(
        TokenResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 3600,
        ),
    )

    private fun errorResponse() = Response.error<TokenResponse>(
        401,
        """{"code":"UNAUTHORIZED","message":"Bad credentials","details":[]}""".toResponseBody(),
    )

    // -------------------------------------------------------------------------
    // Successful login → appLockState unlocked
    // -------------------------------------------------------------------------

    @Test
    fun `login success unlocks the AppLockState`() = runTest {
        coEvery { authApi.login(any()) } returns successResponse()

        val repo = makeRepo()
        repo.login("user@example.com", "password123")

        assertTrue(appLockState.isUnlocked.value)
    }

    @Test
    fun `loginWithGoogle success unlocks the AppLockState`() = runTest {
        coEvery { authApi.googleLogin(LoginRequest(token = "id-token")) } returns successResponse()

        val repo = makeRepo()
        repo.loginWithGoogle("id-token")

        assertTrue(appLockState.isUnlocked.value)
    }

    @Test
    fun `register success unlocks the AppLockState`() = runTest {
        coEvery { authApi.register(any()) } returns successResponse()

        val repo = makeRepo()
        repo.register("Alice", "alice@example.com", "password123")

        assertTrue(appLockState.isUnlocked.value)
    }

    // -------------------------------------------------------------------------
    // Failed login → appLockState stays locked
    // -------------------------------------------------------------------------

    @Test
    fun `login with non-2xx response does NOT unlock AppLockState`() = runTest {
        coEvery { authApi.login(any()) } returns errorResponse()

        val repo = makeRepo()
        repo.login("user@example.com", "wrong-password")

        assertFalse(appLockState.isUnlocked.value)
    }

    @Test
    fun `login with network exception does NOT unlock AppLockState`() = runTest {
        coEvery { authApi.login(any()) } throws java.io.IOException("timeout")

        val repo = makeRepo()
        repo.login("user@example.com", "password123")

        assertFalse(appLockState.isUnlocked.value)
    }

    // -------------------------------------------------------------------------
    // logout() — clears tokens
    // -------------------------------------------------------------------------

    @Test
    fun `logout clears tokens`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null

        val repo = makeRepo()
        repo.logout()

        coVerify { tokenStore.clear() }
    }

    // -------------------------------------------------------------------------
    // logout() — calls backend when refresh token present
    // -------------------------------------------------------------------------

    @Test
    fun `logout calls backend with refresh token when present`() = runTest {
        val refreshToken = "some-refresh-token"
        coEvery { tokenStore.getRefreshToken() } returns refreshToken
        coEvery { authApi.logout(any()) } returns Response.success(Unit)

        val repo = makeRepo()
        repo.logout()

        coVerify { authApi.logout(match { it.token == refreshToken }) }
    }

    @Test
    fun `logout skips backend and still clears and re-locks when no refresh token`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null
        appLockState.unlock()

        val repo = makeRepo()
        repo.logout()

        coVerify(exactly = 0) { authApi.logout(any()) }
        coVerify { tokenStore.clear() }
        assertFalse(appLockState.isUnlocked.value)
    }

    // -------------------------------------------------------------------------
    // logout() — re-locks AppLockState
    // -------------------------------------------------------------------------

    @Test
    fun `logout re-locks AppLockState`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null
        appLockState.unlock() // pre-unlock so the assertion is meaningful

        val repo = makeRepo()
        repo.logout()

        assertFalse(appLockState.isUnlocked.value)
    }

    @Test
    fun `logout still clears and re-locks when backend throws`() = runTest {
        val refreshToken = "refresh-tok"
        coEvery { tokenStore.getRefreshToken() } returns refreshToken
        coEvery { authApi.logout(any()) } throws IOException("network error")
        appLockState.unlock()

        val repo = makeRepo()
        repo.logout()

        coVerify { tokenStore.clear() }
        assertFalse(appLockState.isUnlocked.value)
    }

    // -------------------------------------------------------------------------
    // deleteAccount() — transactional: clear ONLY on backend success
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount success clears tokens and re-locks`() = runTest {
        coEvery { authApi.deleteAccount() } returns Response.success(Unit)
        appLockState.unlock()

        val repo = makeRepo()
        val result = repo.deleteAccount()

        assertTrue(result.isSuccess)
        coVerify { authApi.deleteAccount() }
        coVerify { tokenStore.clear() }
        assertFalse(appLockState.isUnlocked.value)
    }

    @Test
    fun `deleteAccount does NOT clear the session when the backend fails`() = runTest {
        coEvery { authApi.deleteAccount() } returns Response.error(500, "".toResponseBody())
        appLockState.unlock()

        val repo = makeRepo()
        val result = repo.deleteAccount()

        assertTrue(result.isFailure)
        // The account still exists server-side, so the local session must be kept intact.
        coVerify(exactly = 0) { tokenStore.clear() }
        assertTrue(appLockState.isUnlocked.value)
    }

    @Test
    fun `deleteAccount does NOT clear the session when the call throws`() = runTest {
        coEvery { authApi.deleteAccount() } throws IOException("network error")
        appLockState.unlock()

        val repo = makeRepo()
        val result = repo.deleteAccount()

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { tokenStore.clear() }
        assertTrue(appLockState.isUnlocked.value)
    }
}
