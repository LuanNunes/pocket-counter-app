package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.PaymentMethodPrefsStore
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PaymentMethodPrefsRepository] backed by a [PaymentMethodPrefsStore].
 *
 * - [enabledMethods] maps the raw stored names through [PaymentMethodPreferences.resolve] so
 *   callers always receive a non-empty, validated set — even when the store has never been written.
 * - [setEnabled] reads the current resolved set, applies the domain toggle (which enforces the
 *   never-empty guard), and persists only the resulting names.
 */
@Singleton
class LocalPaymentMethodPrefsRepository @Inject constructor(
    private val store: PaymentMethodPrefsStore,
) : PaymentMethodPrefsRepository {

    override val enabledMethods: Flow<Set<PaymentMethod>> =
        store.enabledRaw.map { PaymentMethodPreferences.resolve(it) }

    override suspend fun setEnabled(method: PaymentMethod, enabled: Boolean) {
        val current = PaymentMethodPreferences.resolve(store.enabledRaw.first())
        val updated = PaymentMethodPreferences.toggle(current, method, enabled)
        // toggle already enforces the never-empty guard; skip a no-op write (e.g. disabling the last
        // method, or re-enabling an already-enabled one) so we don't churn DataStore needlessly.
        if (updated == current) return
        store.setEnabledRaw(updated.map { it.name }.toSet())
    }
}
