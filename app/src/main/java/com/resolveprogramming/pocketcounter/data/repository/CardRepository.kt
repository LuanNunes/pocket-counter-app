package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag

interface CardRepository {
    suspend fun getCards(): Result<List<CreditCard>>

    /** The statement (fatura) for [refYearMonth] per card; defaults to the current month. */
    suspend fun getOpenInvoices(refYearMonth: Int = RemoteMappers.currentRefYearMonth()): Result<List<OpenInvoice>>

    /**
     * Persists [tags] onto the invoice line item (PUT items/{itemId}) and, when [learnRule]
     * is set, creates a ClassificationRule for future auto-classification. The item PUT is the
     * success criterion: a failed PUT fails the call; a succeeding PUT with a failed rule
     * POST still succeeds, with [ClassifyOutcome.ruleCreated] flagging the partial failure.
     */
    suspend fun classifyPurchase(
        invoiceId: String,
        itemId: String,
        tags: List<Tag>,
        learnRule: Boolean,
        card: CreditCard,
    ): Result<ClassifyOutcome>

    /** Creates a credit card and returns the mapped domain model. */
    suspend fun addCard(
        name: String,
        brand: String?,
        closingDay: Int?,
        color: String?,
    ): Result<CreditCard>
}

/**
 * Result of [CardRepository.classifyPurchase] when the tag PUT succeeded.
 * [ruleCreated] is false when a rule was requested but its POST failed (partial success).
 */
data class ClassifyOutcome(
    val ruleRequested: Boolean,
    val ruleCreated: Boolean,
)
