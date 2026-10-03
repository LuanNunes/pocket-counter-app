package com.resolveprogramming.pocketcounter.data.repository

import android.content.Context
import android.util.Log
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.GoogleSignInClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionRecoveryTest {

    private val googleSignInClient: GoogleSignInClient = mockk()
    private val authRepository: AuthRepository = mockk()
    private val tokenStore: TokenStore = mockk()
    private val activityContext: Context = mockk()

    private val recovery = SessionRecovery(googleSignInClient, authRepository, tokenStore)

    // SessionRecovery logs discarded failures via android.util.Log, which isn't available on the JVM.
    @Before
    fun stubLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any(), any()) } returns 0
    }

    @After
    fun releaseLog() = unmockkStatic(Log::class)

    @Test
    fun `refresh token present short-circuits to true without touching Google`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns "refresh-token"

        assertTrue(recovery.tryReconnect(activityContext))

        coVerify(exactly = 0) { googleSignInClient.requestIdTokenSilently(any()) }
        coVerify(exactly = 0) { authRepository.loginWithGoogle(any()) }
    }

    @Test
    fun `silent success then login success returns true`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null
        coEvery { googleSignInClient.requestIdTokenSilently(activityContext) } returns Result.success("id-token")
        coEvery { authRepository.loginWithGoogle("id-token") } returns Result.success(Unit)

        assertTrue(recovery.tryReconnect(activityContext))

        coVerify { authRepository.loginWithGoogle("id-token") }
    }

    @Test
    fun `silent failure returns false without calling login`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null
        coEvery { googleSignInClient.requestIdTokenSilently(activityContext) } returns
            Result.failure(RuntimeException("no credential"))

        assertFalse(recovery.tryReconnect(activityContext))

        coVerify(exactly = 0) { authRepository.loginWithGoogle(any()) }
    }

    @Test
    fun `login failure returns false`() = runTest {
        coEvery { tokenStore.getRefreshToken() } returns null
        coEvery { googleSignInClient.requestIdTokenSilently(activityContext) } returns Result.success("id-token")
        coEvery { authRepository.loginWithGoogle("id-token") } returns Result.failure(RuntimeException("backend down"))

        assertFalse(recovery.tryReconnect(activityContext))
    }
}
