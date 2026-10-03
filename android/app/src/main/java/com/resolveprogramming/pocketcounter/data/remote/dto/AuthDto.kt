package com.resolveprogramming.pocketcounter.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
    val token: String? = null,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val name: String,
    val password: String,
)

@Serializable
data class TokenResponse(
    @SerialName("accessToken") val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
    @SerialName("expiresIn") val expiresIn: Long,
    @SerialName("tokenType") val tokenType: String = "Bearer",
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: List<String> = emptyList(),
    val timestamp: String? = null,
)
