package com.resolveprogramming.pocketcounter.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Abstracts the raw string-set persistence of payment-method preferences.
 * The Android DataStore implementation lives in DataStorePaymentMethodPrefsStore (out of scope here).
 * Null means the preference was never set; callers treat that as "use the default".
 */
interface PaymentMethodPrefsStore {
    /** Emits the raw persisted name strings, or null when no value has ever been stored. */
    val enabledRaw: Flow<Set<String>?>

    /** Persists [names] as the new enabled set. Must never be called with an empty set. */
    suspend fun setEnabledRaw(names: Set<String>)
}
