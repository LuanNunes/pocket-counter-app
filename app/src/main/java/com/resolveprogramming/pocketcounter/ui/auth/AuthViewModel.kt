package com.resolveprogramming.pocketcounter.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.resolveprogramming.pocketcounter.data.remote.GoogleSignInClient
import com.resolveprogramming.pocketcounter.data.remote.SignInCancelled
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
    val isGoogleLoading: Boolean = false,
    val errorMessage: String? = null,
    val isRegisterMode: Boolean = false,
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val googleSignInClient: GoogleSignInClient,
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
            val result = run {
                if (current.isRegisterMode) {
                    return@run authRepository.register(current.name, current.email, current.password)
                }
                authRepository.login(current.email, current.password)
            }

            result.fold(
                onSuccess = { /* navigation handled by isLoggedIn flow */ },
                onFailure = { error ->
                    _state.update { it.copy(isLoading = false, errorMessage = mapAuthError(error)) }
                },
            )
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        val current = _state.value
        if (current.isGoogleLoading || current.isLoading) return

        _state.update { it.copy(isGoogleLoading = true, errorMessage = null) }

        viewModelScope.launch {
            val idToken = googleSignInClient.requestIdToken(activityContext).getOrElse { error ->
                val message = "Não foi possível entrar com o Google".takeUnless { error is SignInCancelled }
                _state.update { it.copy(isGoogleLoading = false, errorMessage = message) }
                return@launch
            }

            val message = authRepository.loginWithGoogle(idToken).exceptionOrNull()?.let(::mapGoogleError)
            // On success, navigation is handled by the isLoggedIn flow.
            _state.update { it.copy(isGoogleLoading = false, errorMessage = message) }
        }
    }

    private fun mapAuthError(error: Throwable): String =
        when (val authError = (error as? AuthException)?.error) {
            is AuthError.Api -> run {
                when (authError.code) {
                    "UNAUTHORIZED" -> return@run "E-mail ou senha incorretos"
                    "CONFLICT" -> return@run "Este e-mail já está cadastrado"
                    "VALIDATION_ERROR" -> return@run authError.details.firstOrNull() ?: authError.message
                }
                authError.message
            }
            is AuthError.Network -> "Sem conexão com o servidor"
            null -> "Erro inesperado"
        }

    private fun mapGoogleError(error: Throwable): String =
        when ((error as? AuthException)?.error) {
            is AuthError.Network -> "Sem conexão com o servidor"
            is AuthError.Api, null -> "Não foi possível entrar com o Google"
        }
}
