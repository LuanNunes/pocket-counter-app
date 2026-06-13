package com.resolveprogramming.pocketcounter.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.repository.AuthError
import com.resolveprogramming.pocketcounter.data.repository.AuthException
import com.resolveprogramming.pocketcounter.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegisterMode: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    fun onEmailChange(email: String) {
        _state.update { it.copy(email = email, errorMessage = null) }
    }

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, errorMessage = null) }
    }

    fun onNameChange(name: String) {
        _state.update { it.copy(name = name, errorMessage = null) }
    }

    fun toggleMode() {
        _state.update { it.copy(isRegisterMode = !it.isRegisterMode, errorMessage = null) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return

        if (current.email.isBlank() || current.password.isBlank()) {
            _state.update { it.copy(errorMessage = "Preencha todos os campos") }
            return
        }
        if (current.isRegisterMode && current.name.isBlank()) {
            _state.update { it.copy(errorMessage = "Preencha todos os campos") }
            return
        }
        if (current.password.length < 8) {
            _state.update { it.copy(errorMessage = "A senha deve ter pelo menos 8 caracteres") }
            return
        }

        _state.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val result = if (current.isRegisterMode) {
                authRepository.register(current.name, current.email, current.password)
            } else {
                authRepository.login(current.email, current.password)
            }

            result.fold(
                onSuccess = { /* navigation handled by isLoggedIn flow */ },
                onFailure = { error ->
                    val message = when (val authError = (error as? AuthException)?.error) {
                        is AuthError.Api -> when (authError.code) {
                            "UNAUTHORIZED" -> "E-mail ou senha incorretos"
                            "CONFLICT" -> "Este e-mail já está cadastrado"
                            "VALIDATION_ERROR" -> authError.details.firstOrNull() ?: authError.message
                            else -> authError.message
                        }
                        is AuthError.Network -> "Sem conexão com o servidor"
                        else -> "Erro inesperado"
                    }
                    _state.update { it.copy(isLoading = false, errorMessage = message) }
                },
            )
        }
    }
}
