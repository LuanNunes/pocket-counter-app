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

private val Context.securityDataStore by preferencesDataStore(name = "security_settings")

@Singleton
class BiometricSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val lockEnabledKey = booleanPreferencesKey("biometric_lock_enabled")

    val lockEnabled: Flow<Boolean> = context.securityDataStore.data
        .map { prefs -> prefs[lockEnabledKey] ?: false }

    suspend fun setLockEnabled(value: Boolean) {
        context.securityDataStore.edit { prefs -> prefs[lockEnabledKey] = value }
    }
}
