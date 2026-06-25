package com.resolveprogramming.pocketcounter.domain.model

sealed interface BiometricAvailability {
    data object Available : BiometricAvailability
    data object NoneEnrolled : BiometricAvailability
    data object NoHardware : BiometricAvailability
    data object Unknown : BiometricAvailability
}
