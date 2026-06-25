package com.resolveprogramming.pocketcounter.platform.biometric

import androidx.fragment.app.FragmentActivity
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricAvailability

interface BiometricAuthenticator {
    fun availability(): BiometricAvailability
    suspend fun authenticate(activity: FragmentActivity): BiometricAuthResult
}
