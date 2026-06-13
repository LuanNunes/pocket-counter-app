package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore by preferencesDataStore(name = "auth_tokens")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val accessTokenKey = stringPreferencesKey("access_token")
    private val refreshTokenKey = stringPreferencesKey("refresh_token")

    val isLoggedIn: Flow<Boolean> = context.tokenDataStore.data
        .map { prefs -> prefs[accessTokenKey] != null }

    suspend fun getAccessToken(): String? =
        context.tokenDataStore.data.first()[accessTokenKey]

    suspend fun getRefreshToken(): String? =
        context.tokenDataStore.data.first()[refreshTokenKey]

    /** Display name of the logged-in user, read from the access token's `name` claim
     *  (falls back to `email`). Null when logged out or the token can't be parsed. */
    suspend fun getUserName(): String? =
        getAccessToken()?.let { jwtClaim(it, "name") ?: jwtClaim(it, "email") }

    private fun jwtClaim(token: String, claim: String): String? = runCatching {
        val payload = token.split(".").getOrNull(1) ?: return null
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        JSONObject(json).optString(claim).takeIf { it.isNotBlank() }
    }.getOrNull()

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        context.tokenDataStore.edit { prefs ->
            prefs[accessTokenKey] = accessToken
            prefs[refreshTokenKey] = refreshToken
        }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }
}
