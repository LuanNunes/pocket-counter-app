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
         * [pattern] via CONTAINS and applies [type], [paymentMethod] (+ [cardId]) and [tags].
         *
         * The payment method is learned on purpose. Deriving it per-notification from a "final NNNN"
         * hint only works for card notifications: Uber, PIX and débito messages carry no such hint,
         * leaving classify nothing to work from, so the user re-fixed the method on every single
         * capture. A merchant's payment method is a property of the merchant, not of the message.
         *
         * [cardId] is kept only for [PaymentMethod.CREDIT], mirroring `WizardDraft.withPaymentMethod`.
         */
        fun learned(
            pattern: String,
            type: TransactionType?,
            paymentMethod: PaymentMethod?,
            cardId: String?,
            tags: List<Tag>,
        ): ClassificationRule = ClassificationRule(
            id = null,
            patterns = listOf(pattern),
            matchType = "CONTAINS",
            active = true,
            appliedCount = 0,
            transactionType = type,
            paymentMethod = paymentMethod,
            cardId = cardId.takeIf { paymentMethod == PaymentMethod.CREDIT },
            tags = tags,
            action = RuleAction.SUGGEST,
        )
    }
}
