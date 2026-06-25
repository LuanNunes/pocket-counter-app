package com.resolveprogramming.pocketcounter.data.repository

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
) {
    val isLoggedIn: Flow<Boolean> = tokenStore.isLoggedIn

    suspend fun login(email: String, password: String): Result<Unit> = runAuth {
        authApi.login(LoginRequest(email = email, password = password))
    }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = runAuth {
        authApi.register(RegisterRequest(email = email, name = name, password = password))
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
    }

    private suspend fun runAuth(
        call: suspend () -> retrofit2.Response<com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse>,
    ): Result<Unit> = try {
        val response = call()
        run {
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenStore.saveTokens(body.accessToken, body.refreshToken)
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
