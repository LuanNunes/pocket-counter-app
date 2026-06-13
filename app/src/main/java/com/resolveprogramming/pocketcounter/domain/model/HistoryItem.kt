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
)
