package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw `issuerKey → cardId` persistence for the learned issuer map. Behind an interface so the
 * repository's normalization is unit-testable against a hand-written fake.
 */
interface IssuerCardStore {
    suspend fun getMap(): Map<String, String>

    /**
     * Atomically reads, transforms and rewrites the map in one DataStore transaction so concurrent
     * writers can't lose an update.
     */
    suspend fun update(transform: (Map<String, String>) -> Map<String, String>)
}

private val Context.issuerCardDataStore by preferencesDataStore(name = "issuer_card")

/**
 * Persists the device-local `issuerKey → cardId` map as a single JSON object. The backend never
 * sees this data — it is what lets the second invoice-payment push from an issuer resolve to the
 * card the user picked for the first one.
 */
@Singleton
class DataStoreIssuerCardStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : IssuerCardStore {
    private val mapKey = stringPreferencesKey("issuer_card_map")

    override suspend fun getMap(): Map<String, String> =
        parse(context.issuerCardDataStore.data.first()[mapKey])

    override suspend fun update(transform: (Map<String, String>) -> Map<String, String>) {
        context.issuerCardDataStore.edit { prefs ->
            prefs[mapKey] = JSONObject(transform(parse(prefs[mapKey])) as Map<*, *>).toString()
        }
    }

    private fun parse(raw: String?): Map<String, String> {
        if (raw == null) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getString(it) }
        }.getOrDefault(emptyMap())
    }
}
