package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.CardCandidate
import com.resolveprogramming.pocketcounter.domain.model.CardResolution
import com.resolveprogramming.pocketcounter.domain.model.IntentField
import com.resolveprogramming.pocketcounter.domain.model.IntentReading
import com.resolveprogramming.pocketcounter.domain.model.MissingField
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionIntent
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.ValueSource
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Scripts the next read so a whole ask-queue walk reads as a list of outcomes. Preferred over
 * `mockk` here: that walk scripts many answers in a row, which `coEvery` renders unreadably.
 */
class FakeTransactionIntentRepository : TransactionIntentRepository {

    val calls = mutableListOf<Pair<String, LocalDate>>()

    var nextResult: IntentReadResult = IntentReadResult.Read(intent())

    override suspend fun read(text: String, referenceDate: LocalDate): IntentReadResult {
        calls += text to referenceDate
        return nextResult
    }

    companion object {
        /** A neutral synthetic read: nothing here resembles a real merchant or amount. */
        fun intent(
            type: TransactionType? = TransactionType.EXPENSE,
            amount: BigDecimal? = BigDecimal("10.00"),
            date: LocalDate = LocalDate.of(2026, 6, 4),
            name: String? = "Item",
            paymentMethod: PaymentMethod? = null,
            source: Map<IntentField, ValueSource> = emptyMap(),
            cardStatus: CardResolution = CardResolution.NOT_APPLICABLE,
            resolvedCard: CardCandidate? = null,
            cardCandidates: List<CardCandidate> = emptyList(),
            idTag: String? = null,
            idCategory: String? = null,
            missing: List<MissingField> = emptyList(),
        ): TransactionIntent = TransactionIntent(
            reading = IntentReading(
                type = type,
                amount = amount,
                date = date,
                name = name,
                paymentMethod = paymentMethod,
            ),
            source = source,
            cardStatus = cardStatus,
            resolvedCard = resolvedCard,
            cardCandidates = cardCandidates,
            idTag = idTag,
            idCategory = idCategory,
            missing = missing,
        )
    }
}
