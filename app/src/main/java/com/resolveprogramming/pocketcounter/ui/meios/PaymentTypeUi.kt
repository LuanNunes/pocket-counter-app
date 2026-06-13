package com.resolveprogramming.pocketcounter.ui.meios

import com.resolveprogramming.pocketcounter.ui.components.SegmentOption

/** Maps the backend PaymentTypeEnum names to pt-BR labels and segmented-control indices. */
object PaymentTypeUi {
    val ordered = listOf("CREDIT_CARD", "DEBIT_CARD", "CASH", "PIX", "BANK_TRANSFER")
    private val labels = listOf("Crédito", "Débito", "Dinheiro", "Pix", "Transferência")

    val segments: List<SegmentOption> = labels.map { SegmentOption(it) }

    fun indexOf(type: String): Int = ordered.indexOf(type).coerceAtLeast(0)
    fun typeAt(index: Int): String = ordered.getOrElse(index) { ordered.first() }
    fun label(type: String): String = labels.getOrElse(indexOf(type)) { labels.first() }
    fun isCredit(type: String): Boolean = type == "CREDIT_CARD"
}
