package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
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

    @Test
    fun `default draft has all required fields null`() {
        val draft = WizardDraft()
        assertFalse(draft.isStep1Valid())
        assertFalse(draft.isStep2Valid())
        assertFalse(draft.isStep3Valid())
        assertFalse(draft.isStep4Valid())
    }

    @Test
    fun `step 1 is valid when type is set`() {
        val draft = WizardDraft(type = TransactionType.EXPENSE)
        assertTrue(draft.isStep1Valid())
    }

    @Test
    fun `step 2 is valid only when amount is positive`() {
        val zeroAmount = WizardDraft(amount = BigDecimal.ZERO)
        assertFalse(zeroAmount.isStep2Valid())

        val negativeAmount = WizardDraft(amount = BigDecimal("-10"))
        assertFalse(negativeAmount.isStep2Valid())

        val positiveAmount = WizardDraft(amount = BigDecimal("100.50"))
        assertTrue(positiveAmount.isStep2Valid())
    }

    @Test
    fun `step 3 is valid when payment source is set`() {
        val draft = WizardDraft(idPaymentSource = "itau")
        assertTrue(draft.isStep3Valid())
    }

    @Test
    fun `step 4 is valid when source is set`() {
        val draft = WizardDraft(idSource = "src-ifood")
        assertTrue(draft.isStep4Valid())
    }

    @Test
    fun `withPaymentSourceReset clears source when payment changes`() {
        val draft = WizardDraft(idPaymentSource = "itau", idSource = "src-pao")
        val updated = draft.withPaymentSourceReset("nubank")

        assertEquals("nubank", updated.idPaymentSource)
        assertNull(updated.idSource)
    }

    @Test
    fun `withPaymentSourceReset keeps source when payment is the same`() {
        val draft = WizardDraft(idPaymentSource = "itau", idSource = "src-pao")
        val updated = draft.withPaymentSourceReset("itau")

        assertEquals("itau", updated.idPaymentSource)
        assertEquals("src-pao", updated.idSource)
    }

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

    @Test
    fun `default statusPayment is PAID`() {
        val draft = WizardDraft()
        assertEquals(PaymentStatus.PAID, draft.statusPayment)
    }

    @Test
    fun `fromNotification maps all parsed fields`() {
        val notification = NotificationItem(
            id = "n7", app = "Banco Itaú", channel = NotificationChannel.SMS,
            time = "agora", received = "18:41",
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
            suggestions = ClassificationSuggestion("itau", "src-pao", listOf("tag-1")),
            tokens = emptyList(),
        )

        val draft = WizardDraft.fromNotification(notification)

        assertEquals(TransactionType.EXPENSE, draft.type)
        assertEquals(BigDecimal("153.98"), draft.amount)
        assertEquals(LocalDate.of(2026, 5, 16), draft.date)
        assertEquals("itau", draft.idPaymentSource)
        assertEquals("src-pao", draft.idSource)
        assertEquals(listOf("tag-1"), draft.tagIds)
        assertEquals("IFD*A M GUILHERME CORR", draft.merchant)
        assertEquals(3, draft.installments)
        assertEquals(BigDecimal("51.33"), draft.installmentValue)
    }

    @Test
    fun `fromNotification uses today when date is null`() {
        val notification = NotificationItem(
            id = "n0", app = "App", channel = NotificationChannel.SMS,
            time = "agora", received = "18:41", text = "text",
            status = NotificationStatus.NEEDS_REVIEW,
            parsed = ParsedNotification(
                type = null, amount = null, date = null,
                merchantRaw = null, paymentHint = null,
            ),
            suggestions = ClassificationSuggestion(null, null, emptyList()),
            tokens = emptyList(),
        )

        val draft = WizardDraft.fromNotification(notification)
        assertEquals(LocalDate.now(), draft.date)
    }
}
