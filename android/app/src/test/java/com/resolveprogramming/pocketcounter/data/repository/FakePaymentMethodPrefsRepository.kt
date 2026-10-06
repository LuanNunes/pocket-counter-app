package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [PaymentMethodPrefsRepository] for unit tests.
 * Backed by a [MutableStateFlow] so any collector receives updates synchronously in
 * [kotlinx.coroutines.test.TestScope].
 */
class FakePaymentMethodPrefsRepository(
    initial: Set<PaymentMethod> = PaymentMethodPreferences.default,
) : PaymentMethodPrefsRepository {
    val flow = MutableStateFlow(initial)
    override val enabledMethods: Flow<Set<PaymentMethod>> = flow
    override suspend fun setEnabled(method: PaymentMethod, enabled: Boolean) {
        flow.value = PaymentMethodPreferences.toggle(flow.value, method, enabled)
    }
}
