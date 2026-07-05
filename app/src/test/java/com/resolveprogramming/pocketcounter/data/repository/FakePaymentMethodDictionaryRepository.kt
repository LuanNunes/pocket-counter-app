package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.notification.PaymentMethodResolver

/**
 * In-memory [PaymentMethodDictionaryRepository] for unit tests.
 * Backed by a plain MutableMap; normalization is applied the same way the real impl does it
 * so tests can assert on normalized keys without knowing the impl details.
 */
class FakePaymentMethodDictionaryRepository(
    initial: Map<String, PaymentMethod> = emptyMap(),
) : PaymentMethodDictionaryRepository {
    private val map = initial.toMutableMap()

    override suspend fun getMap(): Map<String, PaymentMethod> = map.toMap()

    override suspend fun learn(tokenText: String, method: PaymentMethod) {
        map[PaymentMethodResolver.normalizeKey(tokenText)] = method
    }

    override suspend fun forget(tokenText: String) {
        map.remove(PaymentMethodResolver.normalizeKey(tokenText))
    }
}
