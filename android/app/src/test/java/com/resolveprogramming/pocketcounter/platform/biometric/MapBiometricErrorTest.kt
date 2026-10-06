package com.resolveprogramming.pocketcounter.platform.biometric

import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricUnavailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive mapping from every BiometricPrompt.ERROR_* int constant to the expected
 * domain-sealed result.
 *
 * Constants are plain Ints (compile-time values); tests do NOT require the Android runtime.
 *
 *  ERROR_NEGATIVE_BUTTON (13), ERROR_USER_CANCELED (10), ERROR_CANCELED (5) → Cancelled
 *  ERROR_LOCKOUT (7), ERROR_LOCKOUT_PERMANENT (9)                           → Unavailable(LOCKOUT)
 *  ERROR_NO_BIOMETRICS (11)                                                  → Unavailable(NONE_ENROLLED)
 *  ERROR_HW_NOT_PRESENT (12), ERROR_HW_UNAVAILABLE (1),
 *  ERROR_NO_DEVICE_CREDENTIAL (14)                                           → Unavailable(NO_HARDWARE)
 *  any other code                                                            → Error(null)
 */
class MapBiometricErrorTest {

    // -------------------------------------------------------------------------
    // Cancelled codes
    // -------------------------------------------------------------------------

    @Test
    fun `ERROR_NEGATIVE_BUTTON maps to Cancelled`() {
        val result = mapBiometricError(13) // BiometricPrompt.ERROR_NEGATIVE_BUTTON

        assertEquals(BiometricAuthResult.Cancelled, result)
    }

    @Test
    fun `ERROR_USER_CANCELED maps to Cancelled`() {
        val result = mapBiometricError(10) // BiometricPrompt.ERROR_USER_CANCELED

        assertEquals(BiometricAuthResult.Cancelled, result)
    }

    @Test
    fun `ERROR_CANCELED maps to Cancelled`() {
        val result = mapBiometricError(5) // BiometricPrompt.ERROR_CANCELED

        assertEquals(BiometricAuthResult.Cancelled, result)
    }

    // -------------------------------------------------------------------------
    // Lockout codes
    // -------------------------------------------------------------------------

    @Test
    fun `ERROR_LOCKOUT maps to Unavailable LOCKOUT`() {
        val result = mapBiometricError(7) // BiometricPrompt.ERROR_LOCKOUT

        assertEquals(BiometricAuthResult.Unavailable(BiometricUnavailable.LOCKOUT), result)
    }

    @Test
    fun `ERROR_LOCKOUT_PERMANENT maps to Unavailable LOCKOUT`() {
        val result = mapBiometricError(9) // BiometricPrompt.ERROR_LOCKOUT_PERMANENT

        assertEquals(BiometricAuthResult.Unavailable(BiometricUnavailable.LOCKOUT), result)
    }

    // -------------------------------------------------------------------------
    // No biometrics enrolled
    // -------------------------------------------------------------------------

    @Test
    fun `ERROR_NO_BIOMETRICS maps to Unavailable NONE_ENROLLED`() {
        val result = mapBiometricError(11) // BiometricPrompt.ERROR_NO_BIOMETRICS

        assertEquals(BiometricAuthResult.Unavailable(BiometricUnavailable.NONE_ENROLLED), result)
    }

    // -------------------------------------------------------------------------
    // No hardware / device credential codes
    // -------------------------------------------------------------------------

    @Test
    fun `ERROR_HW_NOT_PRESENT maps to Unavailable NO_HARDWARE`() {
        val result = mapBiometricError(12) // BiometricPrompt.ERROR_HW_NOT_PRESENT

        assertEquals(BiometricAuthResult.Unavailable(BiometricUnavailable.NO_HARDWARE), result)
    }

    @Test
    fun `ERROR_HW_UNAVAILABLE maps to Unavailable NO_HARDWARE`() {
        val result = mapBiometricError(1) // BiometricPrompt.ERROR_HW_UNAVAILABLE

        assertEquals(BiometricAuthResult.Unavailable(BiometricUnavailable.NO_HARDWARE), result)
    }

    @Test
    fun `ERROR_NO_DEVICE_CREDENTIAL maps to Unavailable NO_HARDWARE`() {
        val result = mapBiometricError(14) // BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL

        assertEquals(BiometricAuthResult.Unavailable(BiometricUnavailable.NO_HARDWARE), result)
    }

    // -------------------------------------------------------------------------
    // Unknown / other error codes
    // -------------------------------------------------------------------------

    @Test
    fun `unknown error code maps to Error`() {
        val result = mapBiometricError(99)

        assertTrue(result is BiometricAuthResult.Error)
    }

    @Test
    fun `ERROR_TIMEOUT maps to Error`() {
        val result = mapBiometricError(3) // BiometricPrompt.ERROR_TIMEOUT

        assertTrue(result is BiometricAuthResult.Error)
    }

    @Test
    fun `ERROR_UNABLE_TO_PROCESS maps to Error`() {
        val result = mapBiometricError(2) // BiometricPrompt.ERROR_UNABLE_TO_PROCESS

        assertTrue(result is BiometricAuthResult.Error)
    }
}
