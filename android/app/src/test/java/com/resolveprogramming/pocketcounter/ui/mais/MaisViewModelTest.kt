package com.resolveprogramming.pocketcounter.ui.mais

import app.cash.turbine.test
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for MaisViewModel.
 *
 * Covers logout and account deletion. Biometric lock logic now lives in ConfiguracoesViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authRepository: AuthRepository = mockk(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() = MaisViewModel(authRepository)

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    fun `logout delegates to authRepository logout`() = runTest {
        coEvery { authRepository.logout() } returns Unit

        val vm = makeViewModel()
        vm.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.logout() }
    }

    @Test
    fun `logout does not mutate MaisUiState`() = runTest {
        coEvery { authRepository.logout() } returns Unit

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val stateBefore = vm.state.value

        vm.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(stateBefore, vm.state.value)
    }

    // -------------------------------------------------------------------------
    // deleteAccount()
    // -------------------------------------------------------------------------

    @Test
    fun `deleteAccount delegates to authRepository deleteAccount`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

        val vm = makeViewModel()
        vm.deleteAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.deleteAccount() }
    }

    @Test
    fun `deleteAccount keeps deletingAccount true on success through the redirect`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.deleteAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        // On success the session clears and the nav host tears this screen down, so the flag stays
        // true to keep the controls disabled during the redirect.
        assertTrue(vm.state.value.deletingAccount)
    }

    @Test
    fun `deleteAccount success emits no AccountDeletionFailed event`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.events.test {
            vm.deleteAccount()
            testDispatcher.scheduler.advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `deleteAccount failure resets the flag and emits AccountDeletionFailed`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.failure(IOException("boom"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.events.test {
            vm.deleteAccount()
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(MaisEvent.AccountDeletionFailed, awaitItem())
        }
        assertFalse(vm.state.value.deletingAccount)
    }

    @Test
    fun `deleteAccount ignores re-entrant calls while a deletion is in flight`() = runTest {
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deleteAccount()
        vm.deleteAccount() // second call — guarded out because deletingAccount is already true
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.deleteAccount() }
    }
}
