package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.PaymentMethodDictionaryStore
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.notification.PaymentMethodResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PaymentMethodDictionaryRepository] backed by a [PaymentMethodDictionaryStore].
 *
 * - [getMap] converts stored name strings to [PaymentMethod] via [PaymentMethod.valueOf]; corrupt
 *   or unknown names are silently dropped rather than thrown, keeping the dictionary robust against
 *   future enum changes or manual data corruption.
 * - [learn] normalizes the key via [PaymentMethodResolver.normalizeKey] before writing so the
 *   read and write paths are always consistent.
 * - [forget] likewise normalizes before delegating to [PaymentMethodDictionaryStore.remove].
 */
@Singleton
class LocalPaymentMethodDictionaryRepository @Inject constructor(
    private val store: PaymentMethodDictionaryStore,
) : PaymentMethodDictionaryRepository {

    override suspend fun getMap(): Map<String, PaymentMethod> =
        store.getRaw().mapNotNull { (key, name) ->
            val method = runCatching { PaymentMethod.valueOf(name) }.getOrNull()
                ?: return@mapNotNull null
            key to method
        }.toMap()

    override suspend fun learn(tokenText: String, method: PaymentMethod) {
        store.put(PaymentMethodResolver.normalizeKey(tokenText), method.name)
    }

    override suspend fun forget(tokenText: String) {
        store.remove(PaymentMethodResolver.normalizeKey(tokenText))
    }
}
