package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class WizardDraftTest {

    // -------------------------------------------------------------------------
    // isStep1Valid — type != null
    // -------------------------------------------------------------------------

    @Test
    fun `isStep1Valid returns false when type is null`() {
        val draft = WizardDraft()
        assertFalse(draft.isStep1Valid())
    }

    @Test
    fun `isStep1Valid returns true when type is set`() {
        val draft = WizardDraft(type = TransactionType.EXPENSE)
        assertTrue(draft.isStep1Valid())
    }

    // -------------------------------------------------------------------------
    // isStep2Valid — amount > 0, plus isFixo/recurrenceDay rules
    // -------------------------------------------------------------------------

    @Test
    fun `isStep2Valid returns false when amount is null`() {
        val draft = WizardDraft(amount = null)
        assertFalse(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns false when amount is zero`() {
        val draft = WizardDraft(amount = BigDecimal.ZERO)
        assertFalse(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns false when amount is negative`() {
        val draft = WizardDraft(amount = BigDecimal("-0.01"))
        assertFalse(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns true when amount is positive and not fixo`() {
        val draft = WizardDraft(amount = BigDecimal("100.50"), isFixo = false)
        assertTrue(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns true when isFixo is false and recurrenceDay is null`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = false, recurrenceDay = null)
        assertTrue(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns false when isFixo is true and recurrenceDay is null`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = true, recurrenceDay = null)
        assertFalse(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns true when isFixo is true and recurrenceDay is 10`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = true, recurrenceDay = 10)
        assertTrue(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns true when isFixo is true and recurrenceDay is 1 (lower boundary)`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = true, recurrenceDay = 1)
        assertTrue(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns true when isFixo is true and recurrenceDay is 31 (upper boundary)`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = true, recurrenceDay = 31)
        assertTrue(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns false when isFixo is true and recurrenceDay is 0 (below range)`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = true, recurrenceDay = 0)
        assertFalse(draft.isStep2Valid())
    }

    @Test
    fun `isStep2Valid returns false when isFixo is true and recurrenceDay is 32 (above range)`() {
        val draft = WizardDraft(amount = BigDecimal("50.00"), isFixo = true, recurrenceDay = 32)
        assertFalse(draft.isStep2Valid())
    }

    // -------------------------------------------------------------------------
    // isStep3Valid — payment is optional; CREDIT requires cardId
    // -------------------------------------------------------------------------

    @Test
    fun `isStep3Valid returns true when paymentMethod is null (payment optional)`() {
        val draft = WizardDraft(paymentMethod = null)
        assertTrue(draft.isStep3Valid())
    }

    @Test
    fun `isStep3Valid returns true for DEBIT without cardId`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.DEBIT, cardId = null)
        assertTrue(draft.isStep3Valid())
    }

    @Test
    fun `isStep3Valid returns true for PIX without cardId`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.PIX, cardId = null)
        assertTrue(draft.isStep3Valid())
    }

    @Test
    fun `isStep3Valid returns false for CREDIT without cardId`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.CREDIT, cardId = null)
        assertFalse(draft.isStep3Valid())
    }

    @Test
    fun `isStep3Valid returns true for CREDIT with cardId set`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.CREDIT, cardId = "card-x")
        assertTrue(draft.isStep3Valid())
    }

    // -------------------------------------------------------------------------
    // isStep4Valid — tags step is always true
    // -------------------------------------------------------------------------

    @Test
    fun `isStep4Valid always returns true regardless of tagIds`() {
        assertTrue(WizardDraft().isStep4Valid())
        assertTrue(WizardDraft(tagIds = listOf("tag-1", "tag-2")).isStep4Valid())
    }

    // -------------------------------------------------------------------------
    // withPaymentMethod
    // -------------------------------------------------------------------------

    @Test
    fun `withPaymentMethod sets paymentMethod on draft`() {
        val draft = WizardDraft()

        val updated = draft.withPaymentMethod(PaymentMethod.PIX)

        assertEquals(PaymentMethod.PIX, updated.paymentMethod)
    }

    @Test
    fun `withPaymentMethod to CREDIT keeps cardId when already set`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.CREDIT, cardId = "card-x")

        val updated = draft.withPaymentMethod(PaymentMethod.CREDIT)

        assertEquals(PaymentMethod.CREDIT, updated.paymentMethod)
        assertEquals("card-x", updated.cardId)
    }

    @Test
    fun `withPaymentMethod to non-CREDIT clears cardId`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.CREDIT, cardId = "card-x")

        val updated = draft.withPaymentMethod(PaymentMethod.DEBIT)

        assertEquals(PaymentMethod.DEBIT, updated.paymentMethod)
        assertNull(updated.cardId)
    }

    @Test
    fun `withPaymentMethod to null clears cardId`() {
        val draft = WizardDraft(paymentMethod = PaymentMethod.CREDIT, cardId = "card-x")

        val updated = draft.withPaymentMethod(null)

        assertNull(updated.paymentMethod)
        assertNull(updated.cardId)
    }

    @Test
    fun `withPaymentMethod CREDIT on INCOME type does not set CREDIT`() {
        val draft = WizardDraft(type = TransactionType.INCOME, paymentMethod = null, cardId = null)

        val updated = draft.withPaymentMethod(PaymentMethod.CREDIT)

        assertNull(updated.paymentMethod)
        assertNull(updated.cardId)
    }

    @Test
    fun `withPaymentMethod non-CREDIT on INCOME type applies normally`() {
        val draft = WizardDraft(type = TransactionType.INCOME, paymentMethod = null)

        val updated = draft.withPaymentMethod(PaymentMethod.PIX)

        assertEquals(PaymentMethod.PIX, updated.paymentMethod)
    }

    @Test
    fun `withPaymentMethod CREDIT on EXPENSE type sets CREDIT`() {
        val draft = WizardDraft(type = TransactionType.EXPENSE, paymentMethod = null)

        val updated = draft.withPaymentMethod(PaymentMethod.CREDIT)

        assertEquals(PaymentMethod.CREDIT, updated.paymentMethod)
    }

    // -------------------------------------------------------------------------
    // withTagToggled
    // -------------------------------------------------------------------------

    @Test
    fun `withTagToggled adds tag when not present`() {
        val draft = WizardDraft(tagIds = listOf("tag-1"))

        val updated = draft.withTagToggled("tag-2")

        assertEquals(listOf("tag-1", "tag-2"), updated.tagIds)
    }

    @Test
    fun `withTagToggled removes tag when already present`() {
        val draft = WizardDraft(tagIds = listOf("tag-1", "tag-2"))

        val updated = draft.withTagToggled("tag-1")

        assertEquals(listOf("tag-2"), updated.tagIds)
    }

    // -------------------------------------------------------------------------
    // withTagSelected
    // -------------------------------------------------------------------------

    @Test
    fun `withTagSelected adds tag when not present`() {
        val draft = WizardDraft(tagIds = listOf("tag-1"))

        val updated = draft.withTagSelected("tag-2")

        assertEquals(listOf("tag-1", "tag-2"), updated.tagIds)
    }

    @Test
    fun `withTagSelected keeps draft unchanged when tag already present`() {
        val draft = WizardDraft(tagIds = listOf("tag-1", "tag-2"))

        val updated = draft.withTagSelected("tag-1")

        assertEquals(draft, updated)
    }

    // -------------------------------------------------------------------------
    // Default field values
    // -------------------------------------------------------------------------

    @Test
    fun `default statusPayment is PAID`() {
        val draft = WizardDraft()
        assertEquals(PaymentStatus.PAID, draft.statusPayment)
    }

    @Test
    fun `default isFixo is false`() {
        val draft = WizardDraft()
        assertFalse(draft.isFixo)
    }

    // -------------------------------------------------------------------------
    // fromNotification
    // -------------------------------------------------------------------------

    private fun notification(
        parsed: ParsedNotification = ParsedNotification(
            type = null, amount = null, date = null, merchantRaw = null, paymentHint = null,
        ),
        suggestions: ClassificationSuggestion = ClassificationSuggestion(),
    ) = NotificationItem(
        id = "n",
        app = "App",
        channel = NotificationChannel.SMS,
        time = "agora",
        received = "10:00",
        text = "text",
        status = NotificationStatus.NEEDS_REVIEW,
        parsed = parsed,
        suggestions = suggestions,
        tokens = emptyList(),
    )

    @Test
    fun `fromNotification maps parsed fields and the suggested tag`() {
        val draft = WizardDraft.fromNotification(
            notification(
                parsed = ParsedNotification(
                    type = TransactionType.EXPENSE,
                    amount = BigDecimal("153.98"),
                    date = LocalDate.of(2026, 5, 16),
                    merchantRaw = "IFD*A M GUILHERME CORR",
                    paymentHint = "PERSON BLACK CASHBAC final 3685",
                    installments = 3,
                    installmentValue = BigDecimal("51.33"),
                ),
                suggestions = ClassificationSuggestion(idTag = "tag-1"),
            ),
        )

        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(BigDecimal("153.98"), draft.amount)
        assertEquals(LocalDate.of(2026, 5, 16), draft.date)
        assertEquals(listOf("tag-1"), draft.tagIds)
        assertEquals("IFD*A M GUILHERME CORR", draft.merchant)
        assertEquals(3, draft.installments)
        assertEquals(BigDecimal("51.33"), draft.installmentValue)
        assertFalse(draft.isFixo)
    }

    @Test
    fun `fromNotification without a suggested tag starts with no tags`() {
        assertEquals(emptyList<String>(), WizardDraft.fromNotification(notification()).tagIds)
    }

    @Test
    fun `fromNotification leaves payment method and card to the resolver`() {
        val draft = WizardDraft.fromNotification(
            notification(suggestions = ClassificationSuggestion(idTag = "tag-1")),
        )

        assertNull(draft.paymentMethod)
        assertNull(draft.cardId)
    }

    @Test
    fun `fromNotification uses today when date is null`() {
        assertEquals(LocalDate.now(), WizardDraft.fromNotification(notification()).date)
    }

    @Test
    fun `fromNotification seeds name and merchant from merchantRaw`() {
        val draft = WizardDraft.fromNotification(
            notification(
                parsed = ParsedNotification(
                    type = TransactionType.EXPENSE,
                    amount = BigDecimal("99.00"),
                    date = LocalDate.of(2026, 6, 25),
                    merchantRaw = "PADARIA DO ZE",
                    paymentHint = null,
                ),
            ),
        )

        assertEquals("PADARIA DO ZE", draft.name)
        assertEquals("PADARIA DO ZE", draft.merchant)
    }

    @Test
    fun `fromNotification seeds null name when merchantRaw is null`() {
        val draft = WizardDraft.fromNotification(notification())

        assertNull(draft.name)
        assertNull(draft.merchant)
    }

    @Test
    fun `fromNotification leaves type unset when the text does not reveal it`() {
        assertNull(WizardDraft.fromNotification(notification()).type)
    }

    // -------------------------------------------------------------------------
    // tag order + teachableTag
    // -------------------------------------------------------------------------

    private fun tag(id: String, kind: TransactionType = TransactionType.EXPENSE) =
        Tag(id = id, name = id, kind = kind)

    @Test
    fun `tagIds keep the order the user selected them in`() {
        val draft = WizardDraft()
            .withTagToggled("c").withTagSelected("a").withTagToggled("b")

        assertEquals(listOf("c", "a", "b"), draft.tagIds)
    }

    @Test
    fun `teachableTag follows selection order, not catalog order`() {
        val catalog = listOf(tag("a"), tag("b"), tag("c"))
        val draft = WizardDraft(type = TransactionType.EXPENSE)
            .withTagToggled("c").withTagToggled("a")

        assertEquals("c", draft.teachableTag(catalog)?.id)
    }

    @Test
    fun `teachableTag skips a selected tag missing from the catalog`() {
        val draft = WizardDraft(type = TransactionType.EXPENSE, tagIds = listOf("gone", "b"))

        assertEquals("b", draft.teachableTag(listOf(tag("b")))?.id)
    }

    @Test
    fun `teachableTag skips income tags and takes the first expense one`() {
        val catalog = listOf(tag("inc", TransactionType.INCOME), tag("exp"))
        val draft = WizardDraft(type = TransactionType.EXPENSE, tagIds = listOf("inc", "exp"))

        assertEquals("exp", draft.teachableTag(catalog)?.id)
    }

    @Test
    fun `teachableTag is null for an income draft`() {
        val draft = WizardDraft(type = TransactionType.INCOME, tagIds = listOf("a"))

        assertNull(draft.teachableTag(listOf(tag("a"))))
    }

    @Test
    fun `teachableTag is null with no tags selected`() {
        assertNull(WizardDraft(type = TransactionType.EXPENSE).teachableTag(listOf(tag("a"))))
    }

    @Test
    fun `teachableTag works while the type is still unset`() {
        val draft = WizardDraft(type = null, tagIds = listOf("a"))

        assertEquals("a", draft.teachableTag(listOf(tag("a")))?.id)
    }

    @Test
    fun `hasUsableName requires a non-blank name with at least one letter`() {
        val results = listOf(null, "", "   ", "29", "Mercado 29")
            .map { WizardDraft(name = it).hasUsableName() }

        assertEquals(listOf(false, false, false, false, true), results)
    }
}
