package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/** How the server came by a value it read from the sentence. */
enum class ValueSource { WRITTEN, INFERRED }

/** A field of a lançamento that can carry a provenance. STATUS is never read: only the user sets it. */
enum class IntentField { TYPE, AMOUNT, DATE, NAME, PAYMENT_METHOD, CARD, TAG, STATUS }

/** A field the server could not read and the client must ask for, one at a time, in server order. */
enum class MissingField { AMOUNT, DESCRIPTION, CARD, TYPE }

/** NOT_APPLICABLE does not mean "no card was mentioned" — never branch on it to decide that. */
enum class CardResolution { RESOLVED, AMBIGUOUS, UNRESOLVED, NOT_APPLICABLE }

data class CardCandidate(val id: String, val name: String) {
    override fun toString(): String = "CardCandidate(redacted)"
}

/** What the server understood. [date] is never null: it echoes the referenceDate the client sent. */
data class IntentReading(
    val type: TransactionType?,
    val amount: BigDecimal?,
    val date: LocalDate,
    val name: String?,
    val paymentMethod: PaymentMethod?,
) {
    override fun toString(): String = "IntentReading(redacted)"
}

/**
 * One read sentence. [source] holds an entry only for a field that has a value, so "absent from the
 * map" is the single representation of "no value, no provenance".
 */
data class TransactionIntent(
    val reading: IntentReading,
    val source: Map<IntentField, ValueSource>,
    val cardStatus: CardResolution,
    val resolvedCard: CardCandidate?,
    val cardCandidates: List<CardCandidate>,
    val idTag: String?,
    /** The category the server matched. Always null for income: its tags carry no context. */
    val idCategory: String?,
    val missing: List<MissingField>,
) {
    override fun toString(): String = "TransactionIntent(missing=$missing, card=$cardStatus)"
}
