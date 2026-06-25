package com.resolveprogramming.pocketcounter.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-scoped in-memory flag that tracks whether the current session has passed
 * biometric/credential verification. Resets to false on every cold start (process death),
 * which is exactly the desired cold-start-lock behavior.
 */
@Singleton
class AppLockState @Inject constructor() {

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    fun unlock() {
        _isUnlocked.value = true
    }
}
