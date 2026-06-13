package com.resolveprogramming.pocketcounter.domain.model

import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class Source(
    val id: String,
    val name: String,
    val idPaymentSource: String,
    val allowsExpense: Boolean,
    val allowsIncome: Boolean,
    val refDayRecurring: Int? = null,
    @Serializable(with = BigDecimalSerializer::class)
    val amount: BigDecimal? = null,
    /** Default tag ids applied to transactions created from this source (backend stores UUIDs). */
    val tags: List<String> = emptyList(),
) {
    val isRecurring: Boolean get() = refDayRecurring != null
}
