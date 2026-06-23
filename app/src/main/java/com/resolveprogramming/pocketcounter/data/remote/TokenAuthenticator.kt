package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.LoginRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApiProvider: Provider<AuthApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // A 401 from an /auth/ endpoint (e.g. the token-refresh call itself, with a dead refresh
        // token) must NOT trigger another refresh — this authenticator is attached to the same
        // client the refresh runs on, so refreshing here recurses into an infinite refresh loop.
        // Give up and clear the session so the app falls back to the login screen.
        if (response.request.url.encodedPath.contains("/auth/")) {
            runBlocking { tokenStore.clear() }
            return null
        }

        if (response.responseCount >= 2) {
            runBlocking { tokenStore.clear() }
            return null
        }

        val newTokens = runBlocking {
            val refreshToken = tokenStore.getRefreshToken() ?: return@runBlocking null
            val result = authApiProvider.get().refresh(LoginRequest(token = refreshToken))
            if (result.isSuccessful) {
                result.body()?.also { tokenStore.saveTokens(it.accessToken, it.refreshToken) }
            } else {
                tokenStore.clear()
                null
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    private val Response.responseCount: Int
        get() {
            var count = 1
            var prior = priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
}
