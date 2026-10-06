package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

data class OpenInvoice(
    val card: CreditCard,
    val total: BigDecimal,
    /** total / limit capped at 1f */
    val usage: Float,
    val closesInDays: Int,
    /** formatted as "dd mmm", e.g. "08 jun" */
    val dueLabel: String,
    val items: List<InvoiceItem>,
)

data class InvoiceItem(
    val transactionId: String,
    /** id of the parent invoice TransactionDto */
    val invoiceId: String,
    /** id of the TransactionItemDto; null on the fallback path (plain credit expense) */
    val itemId: String?,
    val name: String,
    val date: LocalDate,
    val amount: BigDecimal,
    /** current tags on the transaction; empty → "classificar" (amber) in UI */
    val tags: List<Tag>,
    /** null when not installment, e.g. "1/10" */
    val installmentLabel: String?,
)
