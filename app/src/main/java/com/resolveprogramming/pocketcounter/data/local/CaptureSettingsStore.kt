package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.captureDataStore by preferencesDataStore(name = "capture_settings")

@Singleton
class CaptureSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val onboardingSeenKey = booleanPreferencesKey("onboarding_seen")

    val onboardingSeen: Flow<Boolean> = context.captureDataStore.data
        .map { prefs -> prefs[onboardingSeenKey] ?: false }

    suspend fun setOnboardingSeen(value: Boolean) {
        context.captureDataStore.edit { prefs -> prefs[onboardingSeenKey] = value }
    }
}
