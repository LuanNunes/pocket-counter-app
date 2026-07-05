package com.resolveprogramming.pocketcounter.ui.configuracoes

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.BiometricSettingsStore
import com.resolveprogramming.pocketcounter.data.repository.PaymentMethodPrefsRepository
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricAvailability
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
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

sealed interface ConfiguracoesEvent {
    data object ShowEnrollSheet : ConfiguracoesEvent
}

data class ConfiguracoesUiState(
    val enabledMethods: Set<PaymentMethod> = PaymentMethodPreferences.default,
    val lockEnabled: Boolean = false,
    val lockRowState: LockRowState = LockRowState.Enabled,
)

@HiltViewModel
class ConfiguracoesViewModel @Inject constructor(
    private val repository: PaymentMethodPrefsRepository,
    private val authenticator: BiometricAuthenticator,
    private val settingsStore: BiometricSettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ConfiguracoesUiState(lockRowState = deriveRowState(authenticator.availability())),
    )
    val state: StateFlow<ConfiguracoesUiState> = _state.asStateFlow()

    private val _events = Channel<ConfiguracoesEvent>(Channel.BUFFERED)
    val events: Flow<ConfiguracoesEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            repository.enabledMethods.collect { enabled ->
                _state.update { it.copy(enabledMethods = enabled) }
            }
        }
        settingsStore.lockEnabled
            .onEach { enabled -> _state.update { it.copy(lockEnabled = enabled) } }
            .launchIn(viewModelScope)
    }

    fun setPaymentMethodEnabled(method: PaymentMethod, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(method, enabled)
        }
    }

    /** Re-query biometric availability — call when returning to the screen (e.g. ON_RESUME),
     *  so a newly-enrolled biometric flips the row out of the Disabled state. */
    fun refreshLockAvailability() {
        _state.update { it.copy(lockRowState = deriveRowState(authenticator.availability())) }
    }

    fun onToggleLock(activity: FragmentActivity, target: Boolean) {
        if (!target) {
            viewModelScope.launch { settingsStore.setLockEnabled(false) }
            return
        }
        val availability = authenticator.availability()
        when (availability) {
            BiometricAvailability.NoHardware -> Unit
            BiometricAvailability.NoneEnrolled -> {
                viewModelScope.launch { _events.send(ConfiguracoesEvent.ShowEnrollSheet) }
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
