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
 * Raw persistence for the per-source confirmed-transaction counters. Behind an interface so
 * [com.resolveprogramming.pocketcounter.data.repository.ProductiveSourceRepository]'s normalization
 * is unit-testable against a hand-written fake.
 */
interface ProductiveSourceStore {
    suspend fun counts(): Map<String, Int>

    /** Atomic read-modify-write, so two confirms landing together can't lose a count. */
    suspend fun increment(key: String)
}

private val Context.productiveSourceDataStore by preferencesDataStore(name = "productive_sources")

/** Persists the device-local `normalized source key → count` map as a single JSON object. */
@Singleton
class DataStoreProductiveSourceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProductiveSourceStore {
    private val countsKey = stringPreferencesKey("productive_sources")

    override suspend fun counts(): Map<String, Int> =
        parse(context.productiveSourceDataStore.data.first()[countsKey])

    override suspend fun increment(key: String) {
        context.productiveSourceDataStore.edit { prefs ->
            val counts = parse(prefs[countsKey])
            prefs[countsKey] = encode(counts + (key to (counts[key] ?: 0) + 1))
        }
    }

    /** Internal, not private, so the JSON round-trip is unit-testable without a DataStore file. */
    internal fun parse(raw: String?): Map<String, Int> {
        if (raw == null) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            json.keys().asSequence().associateWith { json.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    internal fun encode(counts: Map<String, Int>): String = JSONObject(counts as Map<*, *>).toString()
}
