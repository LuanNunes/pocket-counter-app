package com.resolveprogramming.pocketcounter.platform.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricAvailability
import com.resolveprogramming.pocketcounter.domain.model.BiometricUnavailable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Maps a BiometricPrompt error code (a plain Int constant) to the domain sealed result.
 *
 * Guard-clause style with early returns — no else branches (ForbiddenElse rule).
 */
internal fun mapBiometricError(errorCode: Int): BiometricAuthResult {
    if (
        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
        errorCode == BiometricPrompt.ERROR_CANCELED
    ) {
        return BiometricAuthResult.Cancelled
    }
    if (
        errorCode == BiometricPrompt.ERROR_LOCKOUT ||
        errorCode == BiometricPrompt.ERROR_LOCKOUT_PERMANENT
    ) {
        return BiometricAuthResult.Unavailable(BiometricUnavailable.LOCKOUT)
    }
    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS) {
        return BiometricAuthResult.Unavailable(BiometricUnavailable.NONE_ENROLLED)
    }
    if (
        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
        errorCode == BiometricPrompt.ERROR_HW_UNAVAILABLE ||
        errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
    ) {
        return BiometricAuthResult.Unavailable(BiometricUnavailable.NO_HARDWARE)
    }
    return BiometricAuthResult.Error(null)
}

class AndroidBiometricAuthenticator @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricAuthenticator {

    private val allowedAuthenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    override fun availability(): BiometricAvailability {
        val status = BiometricManager.from(context).canAuthenticate(allowedAuthenticators)
        if (status == BiometricManager.BIOMETRIC_SUCCESS) return BiometricAvailability.Available
        if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) return BiometricAvailability.NoneEnrolled
        if (
            status == BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ||
            status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ||
            status == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED
        ) {
            return BiometricAvailability.NoHardware
        }
        return BiometricAvailability.Unknown
    }

    override suspend fun authenticate(activity: FragmentActivity): BiometricAuthResult =
        suspendCancellableCoroutine { cont ->
            val executor = activity.mainExecutor
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(BiometricAuthResult.Success)
                }

                // onAuthenticationFailed is intentionally NOT overridden: a single non-match is
                // non-terminal — the system prompt stays open and lets the user retry. Resuming
                // here would double-resume the coroutine on the later terminal success/error
                // callback (IllegalStateException). Only Succeeded/Error are terminal.

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(mapBiometricError(errorCode))
                }
            }

            val prompt = BiometricPrompt(activity, executor, callback)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Verificar identidade")
                .setSubtitle("Use biometria ou senha do dispositivo")
                // NOTE: setNegativeButtonText is ILLEGAL when DEVICE_CREDENTIAL is set.
                .setAllowedAuthenticators(allowedAuthenticators)
                .build()

            prompt.authenticate(promptInfo)

            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }
}
