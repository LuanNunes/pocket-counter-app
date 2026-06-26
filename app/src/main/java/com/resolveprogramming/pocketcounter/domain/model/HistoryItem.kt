package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class HistoryItem(
    val id: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val type: TransactionType,
    /** The transaction's OWN tags: null = no own tags, non-null = override. (Series-tag inheritance is not applied at render time today — see [effectiveTagIds].) */
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

    companion object {
        /** Canonical ledger order: displayOrder asc → date desc → id asc (stable tiebreaker). */
        val LEDGER_ORDER: Comparator<HistoryItem> =
            compareBy<HistoryItem> { it.displayOrder }
                .thenByDescending { it.date }
                .thenBy { it.id }
    }
}
