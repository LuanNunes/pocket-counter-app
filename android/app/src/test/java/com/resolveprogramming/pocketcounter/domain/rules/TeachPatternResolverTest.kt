package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TeachPatternResolverTest {

    private fun notification(
        text: String,
        merchantRaw: String? = "IFOOD",
        paymentHint: String? = null,
    ) = NotificationItem(
        id = "notif-1",
        app = "Banco Itaú",
        channel = NotificationChannel.SMS,
        time = "agora",
        received = "10:00",
        text = text,
        status = NotificationStatus.NEEDS_REVIEW,
        parsed = ParsedNotification(
            type = null,
            amount = BigDecimal("49.90"),
            date = LocalDate.of(2026, 6, 12),
            merchantRaw = merchantRaw,
            paymentHint = paymentHint,
        ),
        suggestions = ClassificationSuggestion(tagIds = emptyList(), paymentMethod = null, cardId = null),
        tokens = emptyList(),
    )

    @Test
    fun `resolve returns the draft merchant when it occurs in the notification text`() {
        val draft = WizardDraft(merchant = "IFOOD")
        val notification = notification(text = "Compra IFOOD aprovada R$ 49,90")

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = false)

        assertEquals("IFOOD", pattern)
    }

    @Test
    fun `resolve falls back to the parsed merchantRaw when the draft merchant is null`() {
        val draft = WizardDraft(merchant = null)
        val notification = notification(text = "Compra IFOOD aprovada R$ 49,90", merchantRaw = "IFOOD")

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = false)

        assertEquals("IFOOD", pattern)
    }

    @Test
    fun `resolve returns null when no candidate occurs in the notification text`() {
        val draft = WizardDraft(merchant = null)
        val notification = notification(text = "Compra aprovada R$ 49,90", merchantRaw = "IFOOD")

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = false)

        assertNull(pattern)
    }

    @Test
    fun `resolve for an ignore rule falls back to the payment hint when no merchant candidate exists`() {
        val draft = WizardDraft(merchant = null)
        val notification = notification(
            text = "Compra aprovada no cartão final 3685 R$ 49,90",
            merchantRaw = null,
            paymentHint = "final 3685",
        )

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = true)

        assertEquals("final 3685", pattern)
    }

    @Test
    fun `resolve for a suggest rule does not fall back to the payment hint`() {
        val draft = WizardDraft(merchant = null)
        val notification = notification(
            text = "Compra aprovada no cartão final 3685 R$ 49,90",
            merchantRaw = null,
            paymentHint = "final 3685",
        )

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = false)

        assertNull(pattern)
    }

    @Test
    fun `resolve for an ignore rule refuses a bare card-word payment hint`() {
        val draft = WizardDraft(merchant = null)
        val notification = notification(
            text = "Crédito em conta R$ 1.200,00",
            merchantRaw = null,
            paymentHint = "conta",
        )

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = true)

        assertNull(pattern)
    }

    @Test
    fun `resolve for an ignore rule accepts a bare gateway prefix as pattern`() {
        val draft = WizardDraft(merchant = "Ifd*")
        val notification = notification(text = "Compra IFD*APROVADO R$ 49,90", merchantRaw = "Ifd*")

        val pattern = TeachPatternResolver.resolve(draft, notification, forIgnoreRule = true)

        assertEquals("Ifd*", pattern)
    }
}
