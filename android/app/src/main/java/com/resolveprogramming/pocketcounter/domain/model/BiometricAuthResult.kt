package com.resolveprogramming.pocketcounter.domain.model

sealed interface BiometricAuthResult {
    data object Success : BiometricAuthResult
    data object Failed : BiometricAuthResult
    data object Cancelled : BiometricAuthResult
    data class Unavailable(val reason: BiometricUnavailable) : BiometricAuthResult
    data class Error(val message: String?) : BiometricAuthResult
}
