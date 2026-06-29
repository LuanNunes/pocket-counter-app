package com.resolveprogramming.pocketcounter.domain.model

/** What a matched rule does: SUGGEST pre-fills type/payment/tags; IGNORE auto-ignores the notification. */
enum class RuleAction { SUGGEST, IGNORE }

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
    val action: RuleAction = RuleAction.SUGGEST,
)
