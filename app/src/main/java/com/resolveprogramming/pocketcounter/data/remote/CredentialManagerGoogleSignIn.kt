package com.resolveprogramming.pocketcounter.data.remote

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.resolveprogramming.pocketcounter.BuildConfig
import javax.inject.Inject

/**
 * Credential Manager backing for [GoogleSignInClient]. Requests a Google ID token whose
 * audience is the backend Web client ID, mirroring the web frontend's "choose account" UX
 * (`setFilterByAuthorizedAccounts(false)` → any on-device account is offered).
 */
class CredentialManagerGoogleSignIn @Inject constructor() : GoogleSignInClient {

    override suspend fun requestIdToken(activityContext: Context): Result<String> {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_SERVER_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest(listOf(option))

        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val response = credentialManager.getCredential(activityContext, request)
            val idToken = GoogleIdTokenCredential.createFrom(response.credential.data).idToken
            Result.success(idToken)
        } catch (_: GetCredentialCancellationException) {
            Result.failure(SignInCancelled())
        } catch (e: GetCredentialException) {
            Result.failure(e)
        }
    }
}
