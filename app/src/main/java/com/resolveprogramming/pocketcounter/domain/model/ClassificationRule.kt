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
) {
    companion object {
        /**
         * A brand-new learned rule created by teaching: an active SUGGEST rule that matches
         * [pattern] via CONTAINS and applies [type] + [tags]. It carries no payment method or card
         * on purpose — those are derived per-notification from its "final NNNN" hint, not learned.
         */
        fun learned(
            pattern: String,
            type: TransactionType?,
            tags: List<Tag>,
        ): ClassificationRule = ClassificationRule(
            id = null,
            patterns = listOf(pattern),
            matchType = "CONTAINS",
            active = true,
            appliedCount = 0,
            transactionType = type,
            paymentMethod = null,
            cardId = null,
            tags = tags,
            action = RuleAction.SUGGEST,
        )
    }
}
