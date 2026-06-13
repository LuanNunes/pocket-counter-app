package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag

interface CardRepository {
    suspend fun getCards(): Result<List<CreditCard>>
    suspend fun getOpenInvoices(): Result<List<OpenInvoice>>

    /**
     * Persists [tags] onto the transaction (full-DTO PUT) and, when [learnRule] is set,
     * creates a ClassificationRule for future auto-classification. The tag PUT is the
     * success criterion: a failed PUT fails the call; a succeeding PUT with a failed rule
     * POST still succeeds, with [ClassifyOutcome.ruleCreated] flagging the partial failure.
     */
    suspend fun classifyPurchase(
        transactionId: String,
        tags: List<Tag>,
        learnRule: Boolean,
        card: CreditCard,
    ): Result<ClassifyOutcome>
}

/**
 * Result of [CardRepository.classifyPurchase] when the tag PUT succeeded.
 * [ruleCreated] is false when a rule was requested but its POST failed (partial success).
 */
data class ClassifyOutcome(
    val ruleRequested: Boolean,
    val ruleCreated: Boolean,
)
