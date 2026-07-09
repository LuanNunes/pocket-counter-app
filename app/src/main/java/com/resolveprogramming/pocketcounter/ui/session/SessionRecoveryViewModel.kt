package com.resolveprogramming.pocketcounter.ui.session

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.SessionRecovery
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecoveryState {
    Idle,
    Attempting,
    Recovered,
    Failed,
}

@HiltViewModel
class SessionRecoveryViewModel @Inject constructor(
    private val sessionRecovery: SessionRecovery,
) : ViewModel() {

    private val _state = MutableStateFlow(RecoveryState.Idle)
    val state: StateFlow<RecoveryState> = _state.asStateFlow()

    /**
     * One-shot: silent Google re-auth is attempted at most once. Re-entry is ignored so a
     * persistently-failing silent sign-in can't thrash on recomposition.
     */
    fun attempt(activity: FragmentActivity) {
        if (_state.value != RecoveryState.Idle) return

        _state.value = RecoveryState.Attempting

        viewModelScope.launch {
            val recovered = sessionRecovery.tryReconnect(activity)
            _state.value = if (recovered) RecoveryState.Recovered else RecoveryState.Failed
        }
    }
}
