package com.resolveprogramming.pocketcounter.domain.model

data class ClassificationRule(
    val id: String?,
    val patterns: List<String>,
    val matchType: String?,
    val active: Boolean?,
    val appliedCount: Int,
    val transactionType: TransactionType?,
    val paymentMethod: PaymentMethod?,
    val cardId: String?,
    val tags: List<Tag>,
)
