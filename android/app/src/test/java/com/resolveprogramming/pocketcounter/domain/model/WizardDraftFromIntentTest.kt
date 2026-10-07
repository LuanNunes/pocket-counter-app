package com.resolveprogramming.pocketcounter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class WizardDraftFromIntentTest {

    private fun intent(
        type: TransactionType? = TransactionType.EXPENSE,
        amount: BigDecimal? = BigDecimal("12.34"),
        date: LocalDate = LocalDate.of(2026, 6, 4),
        name: String? = "Item",
        paymentMethod: PaymentMethod? = null,
        resolvedCard: CardCandidate? = null,
        idTag: String? = null,
    ) = TransactionIntent(
        reading = IntentReading(
            type = type,
            amount = amount,
            date = date,
            name = name,
            paymentMethod = paymentMethod,
        ),
        source = emptyMap(),
        cardStatus = CardResolution.NOT_APPLICABLE,
        resolvedCard = resolvedCard,
        cardCandidates = emptyList(),
        idTag = idTag,
        idCategory = null,
        missing = emptyList(),
    )

    @Test
    fun fromIntent_seedsTheReadingIntoTheDraft() {
        val draft = WizardDraft.fromIntent(
            intent(
                type = TransactionType.EXPENSE,
                amount = BigDecimal("12.34"),
                date = LocalDate.of(2026, 6, 4),
                name = "Item",
                paymentMethod = PaymentMethod.PIX,
            ),
        )

        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(BigDecimal("12.34"), draft.amount)
        assertEquals(LocalDate.of(2026, 6, 4), draft.date)
        assertEquals("Item", draft.name)
        assertEquals("Item", draft.merchant)
        assertEquals(PaymentMethod.PIX, draft.paymentMethod)
    }

    /** The endpoint carries the direction; the entity rejects a negative. */
    @Test
    fun fromIntent_negativeAmount_keepsTheMagnitude() {
        val draft = WizardDraft.fromIntent(intent(amount = BigDecimal("-12.34")))

        assertEquals(BigDecimal("12.34"), draft.amount)
    }

    @Test
    fun fromIntent_creditWithAResolvedCard_keepsTheCardId() {
        val draft = WizardDraft.fromIntent(
            intent(
                paymentMethod = PaymentMethod.CREDIT,
                resolvedCard = CardCandidate(id = "card-1", name = "Cartão"),
            ),
        )

        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertEquals("card-1", draft.cardId)
    }

    @Test
    fun fromIntent_withATag_carriesExactlyThatTag() {
        val draft = WizardDraft.fromIntent(intent(idTag = "tag-1"))

        assertEquals(listOf("tag-1"), draft.tagIds)
    }

    @Test
    fun fromIntent_methodOtherThanCredit_dropsTheResolvedCard() {
        val draft = WizardDraft.fromIntent(
            intent(
                paymentMethod = PaymentMethod.DEBIT,
                resolvedCard = CardCandidate(id = "card-1", name = "Cartão"),
            ),
        )

        assertEquals(PaymentMethod.DEBIT, draft.paymentMethod)
        assertNull(draft.cardId)
    }

    @Test
    fun fromIntent_creditWithoutAResolvedCard_hasNoCardId() {
        val draft = WizardDraft.fromIntent(intent(paymentMethod = PaymentMethod.CREDIT))

        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertNull(draft.cardId)
    }

    @Test
    fun fromIntent_incomeReadAsCredit_dropsTheMethodAndTheCard() {
        val draft = WizardDraft.fromIntent(
            intent(
                type = TransactionType.INCOME,
                paymentMethod = PaymentMethod.CREDIT,
                resolvedCard = CardCandidate(id = "card-1", name = "Cartão"),
            ),
        )

        assertNull(draft.paymentMethod)
        assertNull(draft.cardId)
    }

    @Test
    fun fromIntent_withoutATag_carriesNoTags() {
        assertEquals(emptyList<String>(), WizardDraft.fromIntent(intent()).tagIds)
    }

    @Test
    fun fromIntent_carriesNoRecurrenceSeriesOrInstallments() {
        val draft = WizardDraft.fromIntent(intent())

        assertFalse(draft.isFixo)
        assertNull(draft.recurrenceDay)
        assertNull(draft.seriesId)
        assertNull(draft.installments)
        assertNull(draft.installmentValue)
    }

    private fun tag(id: String, kind: TransactionType = TransactionType.EXPENSE) =
        Tag(id = id, name = "Alvo", kind = kind)

    @Test
    fun withTagsKnownIn_dropsASuggestedTagTheCatalogDoesNotHave() {
        val draft = WizardDraft.fromIntent(intent(idTag = "ghost"))

        val cleaned = draft.withTagsKnownIn(listOf(tag("real")))

        assertEquals(emptyList<String>(), cleaned.tagIds)
    }

    @Test
    fun withTagsKnownIn_keepsASuggestedTagTheCatalogHas() {
        val draft = WizardDraft.fromIntent(intent(idTag = "real"))

        val cleaned = draft.withTagsKnownIn(listOf(tag("real")))

        assertEquals(listOf("real"), cleaned.tagIds)
    }

    @Test
    fun withTagsKnownIn_withAnEmptyCatalogDropsEverything() {
        val draft = WizardDraft.fromIntent(intent(idTag = "real"))

        assertEquals(emptyList<String>(), draft.withTagsKnownIn(emptyList()).tagIds)
    }

    @Test
    fun withTagsKnownIn_withNoTagIsTheSameInstance() {
        val draft = WizardDraft.fromIntent(intent())

        assertSame(draft, draft.withTagsKnownIn(listOf(tag("real"))))
    }
}
