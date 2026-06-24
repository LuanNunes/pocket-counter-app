package com.resolveprogramming.pocketcounter.data.remote

import android.content.Context

/**
 * Mints a Google ID token (JWT) via Credential Manager. The token's audience is the
 * backend's Web client ID (see [com.resolveprogramming.pocketcounter.BuildConfig.GOOGLE_SERVER_CLIENT_ID]),
 * so it passes the `POST /api/v1/auth/google` audience check.
 *
 * The activity [Context] is passed per-call (Credential Manager needs the hosting
 * Activity to show its UI), keeping the implementation free of injected Context.
 */
interface GoogleSignInClient {

    /**
     * Returns the Google ID token on success. On user cancellation the failure carries
     * a [SignInCancelled]; any other problem carries a generic exception.
     */
    suspend fun requestIdToken(activityContext: Context): Result<String>
}

/** Sentinel marking a user-cancelled sign-in so callers can suppress the error. */
class SignInCancelled : Exception("Sign-in cancelled by user")
