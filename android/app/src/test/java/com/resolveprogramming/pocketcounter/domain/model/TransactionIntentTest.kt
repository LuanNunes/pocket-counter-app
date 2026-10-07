package com.resolveprogramming.pocketcounter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/** The sentence and everything read out of it are never printable: a stack trace must not carry them. */
class TransactionIntentTest {

    private val reading = IntentReading(
        type = TransactionType.EXPENSE,
        amount = BigDecimal("12.34"),
        date = LocalDate.of(2026, 6, 4),
        name = "Alvo",
        paymentMethod = PaymentMethod.PIX,
    )

    private val card = CardCandidate(id = "card-1", name = "Apelido")

    @Test
    fun toString_ofAReading_leaksNeitherTheNameNorTheAmount() {
        val printed = reading.toString()

        assertFalse(printed.contains("Alvo"))
        assertFalse(printed.contains("12.34"))
    }

    @Test
    fun toString_ofACardCandidate_leaksNeitherItsNameNorItsId() {
        val printed = card.toString()

        assertFalse(printed.contains("Apelido"))
        assertFalse(printed.contains("card-1"))
    }

    @Test
    fun toString_ofAnIntent_keepsOnlyTheNonContentShape() {
        val printed = TransactionIntent(
            reading = reading,
            source = mapOf(IntentField.AMOUNT to ValueSource.WRITTEN),
            cardStatus = CardResolution.RESOLVED,
            resolvedCard = card,
            cardCandidates = listOf(card),
            idTag = "tag-1",
            idCategory = "cat-1",
            missing = listOf(MissingField.CARD),
        ).toString()

        assertFalse(printed.contains("Alvo"))
        assertFalse(printed.contains("Apelido"))
        assertFalse(printed.contains("12.34"))
        assertTrue(printed.contains("CARD"))
        assertTrue(printed.contains("RESOLVED"))
    }
}
