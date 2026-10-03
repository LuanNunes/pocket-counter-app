package com.resolveprogramming.pocketcounter.domain.model

object PaymentMethodPreferences {
    val default: Set<PaymentMethod> = PaymentMethod.entries.toSet() - PaymentMethod.CRYPTO

    /**
     * Converts a persisted set of raw name strings to the live [PaymentMethod] set.
     * - null/empty → [default]
     * - unknown names are silently ignored
     * - if all names are unknown → [default]
     */
    fun resolve(storedNames: Set<String>?): Set<PaymentMethod> {
        if (storedNames.isNullOrEmpty()) return default
        val valid = storedNames.mapNotNull { name ->
            PaymentMethod.entries.firstOrNull { it.name == name }
        }.toSet()
        return if (valid.isEmpty()) default else valid
    }

    /** A method can be disabled only when it is currently enabled AND at least one other is too. */
    fun canDisable(enabled: Set<PaymentMethod>, method: PaymentMethod): Boolean =
        enabled.size > 1 && method in enabled

    /**
     * The methods offered in a selection UI: the [enabled] set, plus [selected] so a method already
     * chosen on an existing item is never dropped just because it was globally disabled — then minus
     * CREDIT on income (an income transaction can't be on a credit card).
     */
    fun selectable(
        enabled: Set<PaymentMethod>,
        selected: PaymentMethod?,
        type: TransactionType?,
    ): List<PaymentMethod> =
        PaymentMethod.entries
            .filter { it in enabled || it == selected }
            .filterNot { it == PaymentMethod.CREDIT && type == TransactionType.INCOME }

    /**
     * Returns the new enabled set after toggling [method].
     * - on=true → adds [method]
     * - on=false → removes [method] unless it is the last one (guard: never empties the set)
     */
    fun toggle(enabled: Set<PaymentMethod>, method: PaymentMethod, on: Boolean): Set<PaymentMethod> {
        if (on) return enabled + method
        if (!canDisable(enabled, method)) return enabled
        return enabled - method
    }
}
