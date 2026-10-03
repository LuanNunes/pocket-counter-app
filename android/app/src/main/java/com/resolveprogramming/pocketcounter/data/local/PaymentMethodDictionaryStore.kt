package com.resolveprogramming.pocketcounter.data.local

/**
 * Abstracts the raw string-map persistence of the learned payment-method dictionary.
 *
 * Stores `normalizedKey → PaymentMethod.name` pairs. The Android DataStore implementation
 * (`DataStorePaymentMethodDictionaryStore`) is out of scope here; tests use a hand-written fake.
 */
interface PaymentMethodDictionaryStore {
    /** Returns the full dictionary as raw strings: normalizedKey → PaymentMethod.name. */
    suspend fun getRaw(): Map<String, String>

    /** Persists or replaces the entry for [key] with the given [methodName]. */
    suspend fun put(key: String, methodName: String)

    /** Removes the entry for [key] if it exists. */
    suspend fun remove(key: String)
}
