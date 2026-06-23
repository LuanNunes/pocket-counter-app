package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
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

    @Test
    fun `fromNotification maps parsed fields and new suggestion fields`() {
        val notification = NotificationItem(
            id = "n7",
            app = "Banco Itaú",
            channel = NotificationChannel.SMS,
            time = "agora",
            received = "18:41",
            text = "Compra aprovada",
            status = NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = TransactionType.EXPENSE,
                amount = BigDecimal("153.98"),
                date = LocalDate.of(2026, 5, 16),
                merchantRaw = "IFD*A M GUILHERME CORR",
                paymentHint = "PERSON BLACK CASHBAC final 3685",
                installments = 3,
                installmentValue = BigDecimal("51.33"),
            ),
            suggestions = ClassificationSuggestion(
                tagIds = listOf("tag-1"),
                paymentMethod = PaymentMethod.CREDIT,
                cardId = "card-abc",
            ),
            tokens = emptyList(),
        )

        val draft = WizardDraft.fromNotification(notification)

        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(BigDecimal("153.98"), draft.amount)
        assertEquals(LocalDate.of(2026, 5, 16), draft.date)
        assertEquals(listOf("tag-1"), draft.tagIds)
        assertEquals("IFD*A M GUILHERME CORR", draft.merchant)
        assertEquals(3, draft.installments)
        assertEquals(BigDecimal("51.33"), draft.installmentValue)
        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertEquals("card-abc", draft.cardId)
        assertFalse(draft.isFixo)
    }

    @Test
    fun `fromNotification uses today when date is null`() {
        val notification = NotificationItem(
            id = "n0",
            app = "App",
            channel = NotificationChannel.SMS,
            time = "agora",
            received = "18:41",
            text = "text",
            status = NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = null, amount = null, date = null,
                merchantRaw = null, paymentHint = null,
            ),
            suggestions = ClassificationSuggestion(emptyList()),
            tokens = emptyList(),
        )

        val draft = WizardDraft.fromNotification(notification)

        assertEquals(LocalDate.now(), draft.date)
    }

    @Test
    fun `fromNotification maps a non-credit suggestion method`() {
        val notification = NotificationItem(
            id = "n1",
            app = "App",
            channel = NotificationChannel.SMS,
            time = "agora",
            received = "10:00",
            text = "Aluguel",
            status = NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = TransactionType.EXPENSE,
                amount = BigDecimal("1200.00"),
                date = LocalDate.of(2026, 6, 1),
                merchantRaw = "IMOBILIARIA",
                paymentHint = null,
            ),
            suggestions = ClassificationSuggestion(
                tagIds = emptyList(),
                paymentMethod = PaymentMethod.PIX,
                cardId = null,
            ),
            tokens = emptyList(),
        )

        val draft = WizardDraft.fromNotification(notification)

        assertFalse(draft.isFixo)
        assertEquals(PaymentMethod.PIX, draft.paymentMethod)
        assertNull(draft.cardId)
    }

    @Test
    fun `fromNotification drops CREDIT and cardId when type is INCOME`() {
        // A misfired classifier suggesting credit on an income must NOT produce a
        // credit draft — fromNotification routes the suggestion through the guard.
        val notification = NotificationItem(
            id = "n2",
            app = "App",
            channel = NotificationChannel.SMS,
            time = "agora",
            received = "10:00",
            text = "Salário",
            status = NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = TransactionType.INCOME,
                amount = BigDecimal("5000.00"),
                date = LocalDate.of(2026, 6, 5),
                merchantRaw = "EMPRESA",
                paymentHint = null,
            ),
            suggestions = ClassificationSuggestion(
                tagIds = emptyList(),
                paymentMethod = PaymentMethod.CREDIT,
                cardId = "card-abc",
            ),
            tokens = emptyList(),
        )

        val draft = WizardDraft.fromNotification(notification)

        assertEquals(TransactionType.INCOME, draft.type)
        assertNull(draft.paymentMethod)
        assertNull(draft.cardId)
    }
}
