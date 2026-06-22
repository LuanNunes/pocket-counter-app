package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class HistoryItem(
    val id: String,
    val date: LocalDate,
    val idSource: String,
    val idPaymentSource: String,
    val amount: BigDecimal,
    val type: TransactionType,
    /** The transaction's OWN tags: null = inherit the source's defaults, non-null = override. */
    val tagIds: List<String>?,
    val statusPayment: PaymentStatus = PaymentStatus.PAID,
    /** Backend-managed manual sort key (group-reorder); 0 until the user reorders. */
    val displayOrder: Int = 0,
    val paymentMethod: PaymentMethod? = null,
    val cardId: String? = null,
    val seriesId: String? = null,
    val name: String? = null,
    val description: String? = null,
) {
    val isFixo: Boolean get() = seriesId != null

    fun displayTitle(): String =
        name?.takeIf { it.isNotBlank() }
            ?: description?.takeIf { it.isNotBlank() }
            ?: "—"
}
