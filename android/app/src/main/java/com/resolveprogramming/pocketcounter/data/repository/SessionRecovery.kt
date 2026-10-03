package com.resolveprogramming.pocketcounter.data.repository

import android.content.Context
import android.util.Log
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.remote.GoogleSignInClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silently re-establishes the session with the previously-authorized Google account after the
 * backend refresh token dies (see [TokenAuthenticator]). This runs BEFORE the login screen so a
 * user who signed in with Google isn't asked to re-authenticate manually on session loss.
 */
@Singleton
class SessionRecovery @Inject constructor(
    private val googleSignInClient: GoogleSignInClient,
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore,
) {
    /** true = session re-established (or already valid); false = must show login. Never throws. */
    suspend fun tryReconnect(activityContext: Context): Boolean {
        if (tokenStore.getRefreshToken() != null) return true

        // Failures fall through to the login screen by contract (no error prompt), but we log the
        // discarded cause so a silent-recovery regression is diagnosable in the field.
        val idToken = googleSignInClient.requestIdTokenSilently(activityContext).getOrElse {
            Log.d(TAG, "silent Google re-auth unavailable", it)
            return false
        }
        return authRepository.loginWithGoogle(idToken)
            .onFailure { Log.d(TAG, "backend re-login after silent re-auth failed", it) }
            .isSuccess
    }

    private companion object {
        const val TAG = "SessionRecovery"
    }
}
