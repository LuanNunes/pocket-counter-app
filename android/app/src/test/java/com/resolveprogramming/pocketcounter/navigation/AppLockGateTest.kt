package com.resolveprogramming.pocketcounter.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The security-critical gate condition: the app is locked ONLY when logged in AND the lock
 * setting is on AND the process is not yet unlocked. Everything else must reach the app.
 */
class AppLockGateTest {

    @Test
    fun `locks when logged in, lock enabled, and not yet unlocked`() {
        assertTrue(shouldLock(isLoggedIn = true, lockEnabled = true, isUnlocked = false))
    }

    @Test
    fun `does not lock once unlocked in this process`() {
        assertFalse(shouldLock(isLoggedIn = true, lockEnabled = true, isUnlocked = true))
    }

    @Test
    fun `does not lock when the setting is off`() {
        assertFalse(shouldLock(isLoggedIn = true, lockEnabled = false, isUnlocked = false))
    }

    @Test
    fun `does not lock when logged out`() {
        assertFalse(shouldLock(isLoggedIn = false, lockEnabled = true, isUnlocked = false))
    }

    @Test
    fun `does not lock while login state is still loading`() {
        assertFalse(shouldLock(isLoggedIn = null, lockEnabled = true, isUnlocked = false))
    }

    @Test
    fun `does not lock while the lock setting is still loading`() {
        assertFalse(shouldLock(isLoggedIn = true, lockEnabled = null, isUnlocked = false))
    }
}
