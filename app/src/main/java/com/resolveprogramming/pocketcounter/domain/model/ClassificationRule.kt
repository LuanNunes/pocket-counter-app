package com.resolveprogramming.pocketcounter.domain.model

data class ClassificationRule(
    val id: String?,
    val pattern: String,
    val idPaymentSource: String?,
    val idSource: String?,
    val transactionType: TransactionType?,
    val tags: List<Tag>,
)
