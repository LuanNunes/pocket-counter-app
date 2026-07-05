package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

/**
 * Exposes the user-configurable payment-method enabled set as a live [Flow] and allows toggling
 * individual methods. Business rules (e.g. the never-empty guard) are enforced by the
 * domain layer ([com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences]).
 */
interface PaymentMethodPrefsRepository {
    /** Emits the current enabled [PaymentMethod] set; never emits an empty set. */
    val enabledMethods: Flow<Set<PaymentMethod>>

    /**
     * Enables or disables [method]. If disabling [method] would leave the set empty the call is
     * silently ignored (domain guard via
     * [com.resolveprogramming.pocketcounter.domain.model.PaymentMethodPreferences.toggle]).
     */
    suspend fun setEnabled(method: PaymentMethod, enabled: Boolean)
}
