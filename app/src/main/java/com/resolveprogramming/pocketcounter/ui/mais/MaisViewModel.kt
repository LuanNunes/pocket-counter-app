package com.resolveprogramming.pocketcounter.ui.mais

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.BiometricSettingsStore
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricAvailability
import com.resolveprogramming.pocketcounter.platform.biometric.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockRowState {
    data object Enabled : LockRowState
    data object Disabled : LockRowState
}

sealed interface MaisEvent {
    data object ShowEnrollSheet : MaisEvent
    data object AccountDeletionFailed : MaisEvent
}

data class MaisUiState(
    val lockEnabled: Boolean = false,
    val lockRowState: LockRowState = LockRowState.Enabled,
    /** True while a delete-account call is in flight — disables the confirm/cancel controls. */
    val deletingAccount: Boolean = false,
)

@HiltViewModel
class MaisViewModel @Inject constructor(
    private val authenticator: BiometricAuthenticator,
    private val settingsStore: BiometricSettingsStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        MaisUiState(lockRowState = deriveRowState(authenticator.availability())),
    )
    val state: StateFlow<MaisUiState> = _state.asStateFlow()

    private val _events = Channel<MaisEvent>(Channel.BUFFERED)
    val events: Flow<MaisEvent> = _events.receiveAsFlow()

    init {
        settingsStore.lockEnabled
            .onEach { enabled -> _state.update { it.copy(lockEnabled = enabled) } }
            .launchIn(viewModelScope)
    }

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

    /** Re-query biometric availability — call when returning to the screen (e.g. ON_RESUME),
     *  so a newly-enrolled biometric flips the row out of the Disabled state. */
    fun refresh() {
        _state.update { it.copy(lockRowState = deriveRowState(authenticator.availability())) }
    }

    fun onToggle(activity: FragmentActivity, target: Boolean) {
        if (!target) {
            viewModelScope.launch { settingsStore.setLockEnabled(false) }
            return
        }
        val availability = authenticator.availability()
        when (availability) {
            BiometricAvailability.NoHardware -> Unit
            BiometricAvailability.NoneEnrolled -> {
                viewModelScope.launch { _events.send(MaisEvent.ShowEnrollSheet) }
            }
            BiometricAvailability.Available,
            BiometricAvailability.Unknown,
            -> viewModelScope.launch { handleAuthForEnable(activity) }
        }
    }

    private suspend fun handleAuthForEnable(activity: FragmentActivity) {
        val result = authenticator.authenticate(activity)
        when (result) {
            BiometricAuthResult.Success -> settingsStore.setLockEnabled(true)
            BiometricAuthResult.Failed -> Unit
            BiometricAuthResult.Cancelled -> Unit
            is BiometricAuthResult.Unavailable -> Unit
            is BiometricAuthResult.Error -> Unit
        }
    }

    private fun deriveRowState(availability: BiometricAvailability): LockRowState =
        when (availability) {
            BiometricAvailability.NoHardware -> LockRowState.Disabled
            BiometricAvailability.Available,
            BiometricAvailability.NoneEnrolled,
            BiometricAvailability.Unknown,
            -> LockRowState.Enabled
        }
}
