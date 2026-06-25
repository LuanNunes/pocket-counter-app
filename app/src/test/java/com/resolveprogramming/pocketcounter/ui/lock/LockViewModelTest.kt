package com.resolveprogramming.pocketcounter.ui.lock

import androidx.fragment.app.FragmentActivity
import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricUnavailable
import com.resolveprogramming.pocketcounter.platform.biometric.BiometricAuthenticator
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for LockViewModel.
 *
 * A FakeBiometricAuthenticator is not needed here because each test only exercises a single
 * authenticate return value — MockK is the right tool.
 *
 * Behaviors:
 *   authenticate → Success     → AppLockState.unlock() called, phase Unlocked, no error
 *   authenticate → Failed      → phase Idle (retry visible), "Não reconhecido" error
 *   authenticate → Cancelled   → phase Idle, no error
 *   authenticate → Unavailable → phase Unavailable(reason), no error
 *   authenticate → Error       → phase Idle, generic error message set
 *   continueAnyway()           → unlock called
 *   logout()                   → authRepository.logout() called
 *   re-entrancy: second authenticate while Prompting → no-op
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val authenticator: BiometricAuthenticator = mockk()
    private val appLockState = AppLockState()
    private val authRepository: AuthRepository = mockk(relaxed = true)
    private val activity: FragmentActivity = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel() = LockViewModel(authenticator, appLockState, authRepository)

    // -------------------------------------------------------------------------
    // BiometricAuthResult.Success
    // -------------------------------------------------------------------------

    @Test
    fun `authenticate Success sets phase to Unlocked`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Success

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockPhase.Unlocked, vm.state.value.phase)
    }

    @Test
    fun `authenticate Success calls appLockState unlock`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Success

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(appLockState.isUnlocked.value)
    }

    @Test
    fun `authenticate Success has no error`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Success

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.error)
    }

    // -------------------------------------------------------------------------
    // BiometricAuthResult.Failed
    // -------------------------------------------------------------------------

    @Test
    fun `authenticate Failed lands on Idle so a retry action is visible`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Failed

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `authenticate Failed sets error message Nao reconhecido`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Failed

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Não reconhecido. Tente de novo.", vm.state.value.error)
    }

    // -------------------------------------------------------------------------
    // BiometricAuthResult.Cancelled
    // -------------------------------------------------------------------------

    @Test
    fun `authenticate Cancelled sets phase to Idle`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Cancelled

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `authenticate Cancelled has no error`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Cancelled

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.error)
    }

    // -------------------------------------------------------------------------
    // BiometricAuthResult.Unavailable
    // -------------------------------------------------------------------------

    @Test
    fun `authenticate Unavailable LOCKOUT sets phase to Unavailable with LOCKOUT reason`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns
            BiometricAuthResult.Unavailable(BiometricUnavailable.LOCKOUT)

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockPhase.Unavailable(BiometricUnavailable.LOCKOUT), vm.state.value.phase)
    }

    @Test
    fun `authenticate Unavailable NO_HARDWARE sets phase to Unavailable with NO_HARDWARE reason`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns
            BiometricAuthResult.Unavailable(BiometricUnavailable.NO_HARDWARE)

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockPhase.Unavailable(BiometricUnavailable.NO_HARDWARE), vm.state.value.phase)
    }

    // -------------------------------------------------------------------------
    // BiometricAuthResult.Error
    // -------------------------------------------------------------------------

    @Test
    fun `authenticate Error sets phase to Idle`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Error("hw error")

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LockPhase.Idle, vm.state.value.phase)
    }

    @Test
    fun `authenticate Error sets a non-null error message`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Error("hw error")

        val vm = makeViewModel()
        vm.authenticate(activity)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.error != null)
    }

    // -------------------------------------------------------------------------
    // continueAnyway()
    // -------------------------------------------------------------------------

    @Test
    fun `continueAnyway calls unlock`() = runTest {
        val vm = makeViewModel()
        vm.continueAnyway()

        assertTrue(appLockState.isUnlocked.value)
    }

    @Test
    fun `continueAnyway sets phase to Unlocked`() = runTest {
        val vm = makeViewModel()
        vm.continueAnyway()

        assertEquals(LockPhase.Unlocked, vm.state.value.phase)
    }

    // -------------------------------------------------------------------------
    // logout()
    // -------------------------------------------------------------------------

    @Test
    fun `logout delegates to authRepository logout`() = runTest {
        val vm = makeViewModel()
        vm.logout()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { authRepository.logout() }
    }

    // -------------------------------------------------------------------------
    // Re-entrancy guard
    // -------------------------------------------------------------------------

    @Test
    fun `second authenticate while Prompting is a no-op`() = runTest {
        coEvery { authenticator.authenticate(activity) } returns BiometricAuthResult.Cancelled

        val vm = makeViewModel()

        // Start first authenticate but don't advance — still Prompting
        vm.authenticate(activity)
        // Phase should now be Prompting (before coroutine completes)
        assertEquals(LockPhase.Prompting, vm.state.value.phase)

        // Call authenticate again while still Prompting
        vm.authenticate(activity)

        testDispatcher.scheduler.advanceUntilIdle()

        // authenticate was called on the authenticator exactly once
        coVerify(exactly = 1) { authenticator.authenticate(activity) }
    }
}
