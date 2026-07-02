package com.resolveprogramming.pocketcounter.ui.mais

import androidx.fragment.app.FragmentActivity
import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.local.BiometricSettingsStore
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricAvailability
import com.resolveprogramming.pocketcounter.platform.biometric.BiometricAuthenticator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
 * Uses a fake BiometricSettingsStore (via MockK) to control lockEnabled flow.
 *
 * Behaviors driven out:
 *   availability Available + auth Success → setLockEnabled(true)
 *   availability Available + auth Cancelled → NOT persisted
 *   availability Available + auth Failed → NOT persisted
 *   availability Available + auth Error → NOT persisted
 *   availability Available + auth Unavailable → NOT persisted
 *   availability NoneEnrolled → enroll event emitted, not persisted
 *   availability NoHardware → row state Disabled, authenticate never called
 *   disable (target=false) → setLockEnabled(false) immediately, no authenticate
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaisViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authenticator: BiometricAuthenticator = mockk()
    private val settingsStore: BiometricSettingsStore = mockk()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val activity: FragmentActivity = mockk()

    private val lockEnabledFlow = MutableStateFlow(false)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { settingsStore.lockEnabled } returns lockEnabledFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() = MaisViewModel(authenticator, settingsStore, authRepository)

    // -------------------------------------------------------------------------
    // availability Available + auth Success → persist true
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target true with Available and Success persists lock enabled`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Success
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsStore.setLockEnabled(true) }
    }

    // -------------------------------------------------------------------------
    // availability Available + auth Cancelled → NOT persisted
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target true with Available and Cancelled does NOT persist`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Cancelled
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsStore.setLockEnabled(any()) }
    }

    // -------------------------------------------------------------------------
    // availability Available + auth Failed → NOT persisted
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target true with Available and Failed does NOT persist`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Failed
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsStore.setLockEnabled(any()) }
    }

    // -------------------------------------------------------------------------
    // availability Available + auth Error → NOT persisted
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target true with Available and Error does NOT persist`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Error(null)
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsStore.setLockEnabled(any()) }
    }

    // -------------------------------------------------------------------------
    // availability Available + auth Unavailable → NOT persisted
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target true with Available and Unavailable does NOT persist`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authenticator.authenticate(activity) } returns
            BiometricAuthResult.Unavailable(com.resolveprogramming.pocketcounter.domain.model.BiometricUnavailable.LOCKOUT)
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsStore.setLockEnabled(any()) }
    }

    // -------------------------------------------------------------------------
    // availability NoneEnrolled → enroll event, NOT persisted
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target true with NoneEnrolled emits enroll event`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.NoneEnrolled
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.events.test {
            assertEquals(MaisEvent.ShowEnrollSheet, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onToggle target true with NoneEnrolled does NOT persist`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.NoneEnrolled
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { settingsStore.setLockEnabled(any()) }
    }

    // -------------------------------------------------------------------------
    // availability NoHardware → Disabled row, authenticate never called
    // -------------------------------------------------------------------------

    @Test
    fun `row state is Disabled when availability is NoHardware`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.NoHardware

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockRowState.Disabled, vm.state.value.lockRowState)
    }

    @Test
    fun `onToggle target true with NoHardware never calls authenticate`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.NoHardware
        coEvery { settingsStore.setLockEnabled(any()) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = true)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { authenticator.authenticate(any()) }
    }

    // -------------------------------------------------------------------------
    // disable (target = false) → setLockEnabled(false), no authenticate
    // -------------------------------------------------------------------------

    @Test
    fun `onToggle target false persists disabled immediately`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { settingsStore.setLockEnabled(false) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsStore.setLockEnabled(false) }
    }

    @Test
    fun `onToggle target false never calls authenticate`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { settingsStore.setLockEnabled(false) } returns Unit

        val vm = makeViewModel()
        vm.onToggle(activity, target = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { authenticator.authenticate(any()) }
    }

    // -------------------------------------------------------------------------
    // lockEnabled state reflects the store flow
    // -------------------------------------------------------------------------

    @Test
    fun `state lockEnabled reflects store flow value`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.lockEnabled)

        lockEnabledFlow.value = true
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, vm.state.value.lockEnabled)
    }

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    fun `logout delegates to authRepository logout`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authRepository.logout() } returns Unit

        val vm = makeViewModel()
        vm.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.logout() }
    }

    @Test
    fun `logout does not mutate MaisUiState`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
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
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

        val vm = makeViewModel()
        vm.deleteAccount()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.deleteAccount() }
    }

    @Test
    fun `deleteAccount keeps deletingAccount true on success through the redirect`() = runTest {
        every { authenticator.availability() } returns BiometricAvailability.Available
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
        every { authenticator.availability() } returns BiometricAvailability.Available
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
        every { authenticator.availability() } returns BiometricAvailability.Available
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
        every { authenticator.availability() } returns BiometricAvailability.Available
        coEvery { authRepository.deleteAccount() } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.deleteAccount()
        vm.deleteAccount() // second call — guarded out because deletingAccount is already true
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { authRepository.deleteAccount() }
    }
}
