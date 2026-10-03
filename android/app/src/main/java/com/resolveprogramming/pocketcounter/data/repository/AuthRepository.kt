package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.AppLockState
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ErrorResponse
import com.resolveprogramming.pocketcounter.data.remote.dto.LoginRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.RegisterRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AuthError {
    data class Api(val code: String, val message: String, val details: List<String> = emptyList()) : AuthError
    data class Network(val cause: Throwable) : AuthError
}

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenStore: TokenStore,
    private val json: Json,
    private val appLockState: AppLockState,
) {
    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    suspend fun login(email: String, password: String): Result<Unit> = runAuth {
        authApi.login(LoginRequest(email = email, password = password))
    }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = runAuth {
        authApi.register(RegisterRequest(email = email, name = name, password = password))
    }

    suspend fun loginWithGoogle(idToken: String): Result<Unit> = runAuth {
        authApi.googleLogin(LoginRequest(token = idToken))
    }

    suspend fun logout() {
        val refreshToken = tokenStore.getRefreshToken()
        if (refreshToken != null) {
            try {
                authApi.logout(LoginRequest(token = refreshToken))
            } catch (_: Exception) {
            }
        }
        tokenStore.clear()
        appLockState.lock()
    }

    /**
     * Permanently deletes the account server-side, then drops the local session. Unlike [logout]
     * this is NOT best-effort: the local session is cleared ONLY after the backend confirms the
     * deletion, so a failed call never strands the user "signed out" of an account that still
     * exists. A non-2xx or network error surfaces as [Result.failure] for the caller to report.
     */
    suspend fun deleteAccount(): Result<Unit> = try {
        val response = authApi.deleteAccount()
        if (!response.isSuccessful) {
            error("Account deletion failed: ${response.code()} ${response.message()}")
        }
        tokenStore.clear()
        appLockState.lock()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun runAuth(
        call: suspend () -> retrofit2.Response<com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse>,
    ): Result<Unit> = try {
        val response = call()
        run {
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenStore.saveTokens(body.accessToken, body.refreshToken)
                appLockState.unlock()
                return@run Result.success(Unit)
            }
            val errorBody = response.errorBody()?.string()
            val authError = errorBody?.let {
                try {
                    val err = json.decodeFromString<ErrorResponse>(it)
                    AuthError.Api(err.code, err.message, err.details)
                } catch (_: Exception) {
                    AuthError.Api("UNKNOWN", response.message())
                }
            } ?: AuthError.Api("UNKNOWN", response.message())
            Result.failure(AuthException(authError))
        }
    } catch (e: Exception) {
        Result.failure(AuthException(AuthError.Network(e)))
    }
}

class AuthException(val error: AuthError) : Exception(
    when (error) {
        is AuthError.Api -> error.message
        is AuthError.Network -> error.cause.message
    },
)
