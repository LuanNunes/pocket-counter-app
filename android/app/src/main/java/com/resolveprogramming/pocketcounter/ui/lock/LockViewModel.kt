package com.resolveprogramming.pocketcounter.ui.lock

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import com.resolveprogramming.pocketcounter.domain.model.BiometricAuthResult
import com.resolveprogramming.pocketcounter.domain.model.BiometricUnavailable
import com.resolveprogramming.pocketcounter.platform.biometric.BiometricAuthenticator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LockPhase {
    data object Prompting : LockPhase
    data object Idle : LockPhase
    data class Unavailable(val reason: BiometricUnavailable) : LockPhase
    data object Unlocked : LockPhase
}

data class LockUiState(
    val phase: LockPhase = LockPhase.Idle,
    val error: String? = null,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val authenticator: BiometricAuthenticator,
    private val appLockState: AppLockState,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun authenticate(activity: FragmentActivity) {
        val current = _state.value
        if (current.phase == LockPhase.Prompting) return

        _state.update { it.copy(phase = LockPhase.Prompting, error = null) }

        viewModelScope.launch {
            val result = authenticator.authenticate(activity)
            _state.update { applyResult(result) }
        }
    }

    fun continueAnyway() {
        appLockState.unlock()
        _state.update { it.copy(phase = LockPhase.Unlocked) }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    private fun applyResult(result: BiometricAuthResult): LockUiState = when (result) {
        BiometricAuthResult.Success -> {
            appLockState.unlock()
            LockUiState(phase = LockPhase.Unlocked)
        }
        // The system prompt handles in-prompt retries itself; if a Failed ever surfaces here,
        // land on Idle so a "Desbloquear" retry action is visible (never strand on Prompting).
        BiometricAuthResult.Failed -> LockUiState(
            phase = LockPhase.Idle,
            error = "Não reconhecido. Tente de novo.",
        )
        BiometricAuthResult.Cancelled -> LockUiState(phase = LockPhase.Idle)
        is BiometricAuthResult.Unavailable -> LockUiState(
            phase = LockPhase.Unavailable(result.reason),
        )
        is BiometricAuthResult.Error -> LockUiState(
            phase = LockPhase.Idle,
            error = result.message ?: "Erro ao verificar identidade.",
        )
    }
}
