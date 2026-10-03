package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod

/**
 * Device-local dictionary that maps a normalized token text to a [PaymentMethod].
 *
 * Keys are always normalized (see [com.resolveprogramming.pocketcounter.domain.notification.PaymentMethodResolver.normalizeKey])
 * so lookups and writes always agree regardless of the casing/punctuation seen at call time.
 */
interface PaymentMethodDictionaryRepository {
    /** Returns the full learned dictionary: normalizedKey → [PaymentMethod]. */
    suspend fun getMap(): Map<String, PaymentMethod>

    /**
     * Stores [method] under the normalized form of [tokenText].
     * Replaces any prior entry for the same normalized key.
     */
    suspend fun learn(tokenText: String, method: PaymentMethod)

    /** Removes the entry for the normalized form of [tokenText], if any. */
    suspend fun forget(tokenText: String)
}
