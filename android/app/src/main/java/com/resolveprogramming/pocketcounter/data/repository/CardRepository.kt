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
     * is set, teaches a rule for the merchant. The item PUT is the success criterion: a failed PUT
     * fails the call; a succeeding PUT with a failed rule write still succeeds, reported as
     * [PurchaseClassifyOutcome.RuleFailed].
     *
     * A rule carries one tag, so [tags] must arrive in selection order: the first EXPENSE tag is the
     * one taught, and the others apply to this purchase only. The rule matches that merchant on every
     * card and on captured notifications too — it has no scope.
     */
    suspend fun classifyPurchase(
        invoiceId: String,
        itemId: String,
        tags: List<Tag>,
        learnRule: Boolean,
    ): Result<PurchaseClassifyOutcome>

    /** Creates a credit card and returns the mapped domain model. */
    suspend fun addCard(
        name: String,
        brand: String?,
        closingDay: Int?,
        color: String?,
    ): Result<CreditCard>
}

/** Result of [CardRepository.classifyPurchase] when the tag PUT succeeded. */
sealed interface PurchaseClassifyOutcome {
    data object TagsOnly : PurchaseClassifyOutcome
    data object RuleCreated : PurchaseClassifyOutcome

    /** The server already had a rule for that merchant, which is what the user wanted. */
    data object RuleAlreadyExisted : PurchaseClassifyOutcome

    /** A rule was requested but could not be written (no teachable tag, unusable pattern, or a real error). */
    data object RuleFailed : PurchaseClassifyOutcome
}
