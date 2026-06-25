package com.resolveprogramming.pocketcounter.ui.auth

import android.content.Context
import com.resolveprogramming.pocketcounter.data.remote.GoogleSignInClient
import com.resolveprogramming.pocketcounter.data.remote.SignInCancelled
import com.resolveprogramming.pocketcounter.data.repository.AuthError
import com.resolveprogramming.pocketcounter.data.repository.AuthException
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Tests for AuthViewModel.signInWithGoogle covering the four branches:
 *   success           → loginWithGoogle called, isGoogleLoading false, no error
 *   cancel sentinel   → no error message, isGoogleLoading false
 *   other cred failure→ generic "Não foi possível entrar com o Google"
 *   repository failure→ reuses the shared AuthError → Portuguese mapping
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val googleSignInClient: GoogleSignInClient = mockk()
    private val context: Context = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() = AuthViewModel(authRepository, googleSignInClient)

    @Test
    fun `success calls loginWithGoogle, ends not loading, no error`() = runTest {
        coEvery { googleSignInClient.requestIdToken(context) } returns Result.success("id-token")
        coEvery { authRepository.loginWithGoogle("id-token") } returns Result.success(Unit)

        val vm = makeViewModel()
        vm.signInWithGoogle(context)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.loginWithGoogle("id-token") }
        assertFalse(vm.state.value.isGoogleLoading)
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `cancel sentinel produces no error message`() = runTest {
        coEvery { googleSignInClient.requestIdToken(context) } returns Result.failure(SignInCancelled())

        val vm = makeViewModel()
        vm.signInWithGoogle(context)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.errorMessage)
        assertFalse(vm.state.value.isGoogleLoading)
        coVerify(exactly = 0) { authRepository.loginWithGoogle(any()) }
    }

    @Test
    fun `other credential failure shows generic google error`() = runTest {
        coEvery { googleSignInClient.requestIdToken(context) } returns
            Result.failure(RuntimeException("no credential"))

        val vm = makeViewModel()
        vm.signInWithGoogle(context)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Não foi possível entrar com o Google", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isGoogleLoading)
    }

    @Test
    fun `repository api failure shows google-specific message, not the password message`() = runTest {
        coEvery { googleSignInClient.requestIdToken(context) } returns Result.success("id-token")
        coEvery { authRepository.loginWithGoogle("id-token") } returns
            Result.failure(AuthException(AuthError.Api("UNAUTHORIZED", "unauthorized")))

        val vm = makeViewModel()
        vm.signInWithGoogle(context)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Não foi possível entrar com o Google", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isGoogleLoading)
    }

    @Test
    fun `repository network failure shows connection message`() = runTest {
        coEvery { googleSignInClient.requestIdToken(context) } returns Result.success("id-token")
        coEvery { authRepository.loginWithGoogle("id-token") } returns
            Result.failure(AuthException(AuthError.Network(RuntimeException("timeout"))))

        val vm = makeViewModel()
        vm.signInWithGoogle(context)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sem conexão com o servidor", vm.state.value.errorMessage)
        assertFalse(vm.state.value.isGoogleLoading)
    }
}
