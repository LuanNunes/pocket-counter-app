package com.resolveprogramming.pocketcounter.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PaymentSource(
    val id: String,
    val name: String,
    val sub: String,
    val kind: PaymentSourceKind,
    val color: Long,
    /** Raw backend PaymentTypeEnum name (CREDIT_CARD/DEBIT_CARD/CASH/PIX/BANK_TRANSFER). */
    val type: String = "CREDIT_CARD",
    val allowsIncome: Boolean = false,
    val allowsExpense: Boolean = false,
    /** Day of month the credit-card bill closes (credit only); null otherwise. */
    val refDayBill: Int? = null,
)
