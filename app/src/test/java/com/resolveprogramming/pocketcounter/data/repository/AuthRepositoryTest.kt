package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.LoginRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

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
}
