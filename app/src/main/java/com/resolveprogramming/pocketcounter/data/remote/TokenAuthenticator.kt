package com.resolveprogramming.pocketcounter.data.remote

import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.api.AuthApi
import com.resolveprogramming.pocketcounter.data.remote.dto.LoginRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class TokenAuthenticator @Inject constructor(
    private val tokenStore: TokenStore,
    private val authApiProvider: Provider<AuthApi>,
) : Authenticator {

    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        // A 401 from an /auth/ endpoint (e.g. the token-refresh call itself, with a dead refresh
        // token) must NOT trigger another refresh — this authenticator is attached to the same
        // client the refresh runs on, so refreshing here recurses into an infinite refresh loop.
        // Give up and clear the session so the app falls back to the login screen.
        if (response.request.url.encodedPath.contains("/auth/")) {
            runBlocking { tokenStore.clear() }
            return null
        }

        if (response.responseCount >= 2) return null

        val failedBearer = response.request.header("Authorization")?.removePrefix("Bearer ")
        val freshBearer = runBlocking { freshAccessTokenOrNull(failedBearer) } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshBearer")
            .build()
    }

    /**
     * Acquires [refreshMutex] and either:
     * - Returns the already-rotated token immediately when another coroutine already refreshed
     *   (detected by the stored access token differing from [failedBearer], provided
     *   [failedBearer] is non-null — a null bearer always proceeds to refresh).
     * - Calls the refresh endpoint, saves the new token pair on success, and returns the
     *   new access token.
     * - Clears the session and returns null when the server rejects the refresh token (401/403).
     * - Returns null without clearing when the server is unavailable (5xx) or for any other
     *   non-auth failure, preserving the session for the next attempt.
     */
    private suspend fun freshAccessTokenOrNull(failedBearer: String?): String? =
        refreshMutex.withLock {
            val currentAccess = tokenStore.getAccessToken()

            // Short-circuit: another coroutine already refreshed while we were waiting for the lock.
            // Only applicable when failedBearer is non-null; a null bearer cannot be compared.
            if (failedBearer != null && currentAccess != null && currentAccess != failedBearer) {
                return@withLock currentAccess
            }

            val refreshToken = tokenStore.getRefreshToken() ?: return@withLock null

            val result = try {
                authApiProvider.get().refresh(LoginRequest(token = refreshToken))
            } catch (_: IOException) {
                // Network failure: keep session intact so the app can retry later.
                return@withLock null
            }

            when {
                result.isSuccessful -> {
                    val body = result.body() ?: return@withLock null
                    tokenStore.saveTokens(body.accessToken, body.refreshToken)
                    body.accessToken
                }

                result.code() in SESSION_INVALID_CODES -> {
                    // The refresh token itself is rejected — session is permanently invalid.
                    tokenStore.clear()
                    null
                }

                else -> {
                    // 5xx or other transient errors: do not clear the session.
                    null
                }
            }
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

    private companion object {
        // Refresh responses that mean the refresh token is permanently rejected → clear the session.
        val SESSION_INVALID_CODES = setOf(401, 403)
    }
}
