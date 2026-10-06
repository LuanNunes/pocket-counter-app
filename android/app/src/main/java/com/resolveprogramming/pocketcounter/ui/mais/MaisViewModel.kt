package com.resolveprogramming.pocketcounter.ui.mais

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MaisEvent {
    data object AccountDeletionFailed : MaisEvent
}

data class MaisUiState(
    /** True while a delete-account call is in flight — disables the confirm/cancel controls. */
    val deletingAccount: Boolean = false,
)

@HiltViewModel
class MaisViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MaisUiState())
    val state: StateFlow<MaisUiState> = _state.asStateFlow()

    private val _events = Channel<MaisEvent>(Channel.BUFFERED)
    val events: Flow<MaisEvent> = _events.receiveAsFlow()

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    /**
     * Permanently deletes the account. On success the repository clears the session token, which the
     * nav host observes to route back to sign-in — so this screen is torn down and [deletingAccount]
     * intentionally stays true through the redirect. On failure the flag resets and a
     * [MaisEvent.AccountDeletionFailed] is emitted so the screen can surface an error. Re-entrant
     * calls are ignored while a deletion is already in flight.
     */
    fun deleteAccount() {
        if (_state.value.deletingAccount) return
        // Set the flag synchronously so a rapid second tap is guarded out before the coroutine runs.
        _state.update { it.copy(deletingAccount = true) }
        viewModelScope.launch {
            authRepository.deleteAccount().onFailure {
                _state.update { it.copy(deletingAccount = false) }
                _events.send(MaisEvent.AccountDeletionFailed)
            }
        }
    }
}
