package com.resolveprogramming.pocketcounter.data.local

import com.resolveprogramming.pocketcounter.data.session.SessionScopedStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries a sentence quick-add could not read into the manual form, which lives on another screen.
 * A [StateFlow] rather than an event: Transações may not be composed yet when the user escapes, and
 * its ViewModel then reads the pending value on creation.
 *
 * In memory only, cleared on [consume]: the sentence must never reach disk, so no DataStore and no
 * nav argument.
 */
@Singleton
class ManualEntryRelay @Inject constructor() : SessionScopedStore {

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun seed(text: String) {
        _pending.value = text
    }

    fun consume() {
        _pending.value = null
    }

    override fun clearForSession() = consume()
}
