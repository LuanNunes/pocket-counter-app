package com.resolveprogramming.pocketcounter.data.remote.api

import com.resolveprogramming.pocketcounter.data.remote.dto.LoginRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.RegisterRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.TokenResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<TokenResponse>

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<TokenResponse>

    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: LoginRequest): Response<TokenResponse>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: LoginRequest): Response<TokenResponse>

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body request: LoginRequest): Response<Unit>
}
