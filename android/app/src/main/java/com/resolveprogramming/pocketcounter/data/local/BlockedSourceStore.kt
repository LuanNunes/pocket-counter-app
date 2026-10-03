package com.resolveprogramming.pocketcounter.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.resolveprogramming.pocketcounter.domain.model.BlockedSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Raw persistence for the blocked-sources list. Behind an interface so [BlockedSourceRepository]'s
 * normalization is unit-testable against a hand-written fake.
 */
interface BlockedSourceStore {
    val blocked: Flow<List<BlockedSource>>

    /**
     * Atomically reads, transforms and rewrites the list in one DataStore transaction so concurrent
     * writers can't lose an update.
     */
    suspend fun update(transform: (List<BlockedSource>) -> List<BlockedSource>)
}

private val Context.blockedSourceDataStore by preferencesDataStore(name = "blocked_sources")

/** Persists the device-local blocked-sources list as a single JSON array. Never sent to the backend. */
@Singleton
class DataStoreBlockedSourceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : BlockedSourceStore {
    private val blockedKey = stringPreferencesKey("blocked_sources")

    override val blocked: Flow<List<BlockedSource>> =
        context.blockedSourceDataStore.data.map { prefs -> parse(prefs[blockedKey]) }

    override suspend fun update(transform: (List<BlockedSource>) -> List<BlockedSource>) {
        context.blockedSourceDataStore.edit { prefs ->
            prefs[blockedKey] = encode(transform(parse(prefs[blockedKey])))
        }
    }

    /** Internal, not private, so the JSON round-trip is unit-testable without a DataStore file. */
    internal fun parse(raw: String?): List<BlockedSource> {
        if (raw == null) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                BlockedSource(
                    label = obj.getString("label"),
                    blockedAt = Instant.parse(obj.getString("blockedAt")),
                )
            }
        }.getOrDefault(emptyList())
    }

    internal fun encode(sources: List<BlockedSource>): String {
        val array = JSONArray()
        sources.forEach { source ->
            array.put(
                JSONObject()
                    .put("label", source.label)
                    .put("blockedAt", source.blockedAt.toString()),
            )
        }
        return array.toString()
    }
}
