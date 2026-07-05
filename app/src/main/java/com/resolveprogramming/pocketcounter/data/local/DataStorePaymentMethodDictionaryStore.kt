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

private val Context.paymentMethodDictionaryDataStore by preferencesDataStore(name = "payment_method_dictionary")

/**
 * DataStore-backed [PaymentMethodDictionaryStore]. Persists the learned
 * `normalizedToken → PaymentMethod.name` map as a single JSON object; the backend never sees it.
 * [put]/[remove] use an atomic read-modify-write so concurrent writes can't lose an update.
 */
@Singleton
class DataStorePaymentMethodDictionaryStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : PaymentMethodDictionaryStore {

    private val mapKey = stringPreferencesKey("payment_method_dictionary_map")

    override suspend fun getRaw(): Map<String, String> =
        parse(context.paymentMethodDictionaryDataStore.data.first()[mapKey])

    override suspend fun put(key: String, methodName: String) {
        context.paymentMethodDictionaryDataStore.edit { prefs ->
            val updated = parse(prefs[mapKey]) + (key to methodName)
            prefs[mapKey] = JSONObject(updated as Map<*, *>).toString()
        }
    }

    override suspend fun remove(key: String) {
        context.paymentMethodDictionaryDataStore.edit { prefs ->
            val updated = parse(prefs[mapKey]) - key
            prefs[mapKey] = JSONObject(updated as Map<*, *>).toString()
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
