package com.resolveprogramming.pocketcounter.data.local

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for AppLockState.
 *
 * Behaviors driven out:
 *   default is locked (false)
 *   unlock() emits true
 *   unlock() is idempotent (second call stays true, no extra emission needed)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppLockStateTest {

    private fun makeState() = AppLockState()

    @Test
    fun `default state is locked`() = runTest {
        val state = makeState()

        assertFalse(state.isUnlocked.value)
    }

    @Test
    fun `unlock emits true`() = runTest {
        val state = makeState()

        state.isUnlocked.test {
            assertFalse(awaitItem()) // initial false

            state.unlock()

            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unlock is idempotent — second call does not emit a new value`() = runTest {
        val state = makeState()
        state.unlock()

        state.isUnlocked.test {
            assertTrue(awaitItem()) // already unlocked

            state.unlock() // second call — same value, no emission via StateFlow

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
