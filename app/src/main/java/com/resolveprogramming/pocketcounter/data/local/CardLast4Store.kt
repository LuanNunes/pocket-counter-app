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

private val Context.cardLast4DataStore by preferencesDataStore(name = "card_last4")

/**
 * Persists the device-local `cardId → last4` map as a single JSON object. The backend never
 * sees this data — it is a local convenience used to prefill CREDIT + card from "final NNNN"
 * notification hints.
 */
@Singleton
class CardLast4Store @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mapKey = stringPreferencesKey("card_last4_map")

    suspend fun getMap(): Map<String, String> =
        parse(context.cardLast4DataStore.data.first()[mapKey])

    /**
     * Atomically reads, transforms, and rewrites the map within a single DataStore transaction
     * so concurrent writers (add-card vs. wizard assign) can't lose an update.
     */
    suspend fun update(transform: (Map<String, String>) -> Map<String, String>) {
        context.cardLast4DataStore.edit { prefs ->
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
