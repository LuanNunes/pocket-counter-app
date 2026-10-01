package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NotificationCardResolverTest {

    private fun notification(paymentHint: String? = null, app: String = "Banco Itaú") = NotificationItem(
        id = "n1",
        app = app,
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        text = "Compra aprovada R$ 49,90",
        status = NotificationStatus.AUTO,
        parsed = ParsedNotification(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("49.90"),
            date = LocalDate.of(2026, 6, 29),
            merchantRaw = "IFOOD",
            paymentHint = paymentHint,
        ),
        suggestions = ClassificationSuggestion(tagIds = emptyList()),
        tokens = emptyList(),
    )

    private fun card(id: String, name: String) = CreditCard(
        id = id,
        name = name,
        brand = "Mastercard",
        last4 = "0000",
        gradientStart = 0xFF000000L,
        gradientEnd = 0xFF000000L,
        limit = BigDecimal("1000.00"),
        billDay = 10,
    )

    private fun draft(
        method: PaymentMethod? = PaymentMethod.CREDIT,
        cardId: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
    ) = WizardDraft(type = type, paymentMethod = method, cardId = cardId)

    private val itau = card("card-itau", "Itaú")
    private val nubank = card("card-nubank", "Nubank")

    @Test
    fun `a last4 hit beats the card a rule provided`() {
        val result = resolveDraftCard(
            draft(cardId = "card-nubank"),
            notification(paymentHint = "final 3685"),
            CardEvidence(last4Map = mapOf("card-itau" to "3685", "card-nubank" to "1111")),
        )

        assertEquals("card-itau", result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `the issuer resolves the card when there is no last4 hint`() {
        val result = resolveDraftCard(
            draft(),
            notification(),
            CardEvidence(cards = listOf(itau, nubank)),
        )

        assertEquals("card-itau", result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `the rule's card is kept when neither last4 nor issuer resolves`() {
        val result = resolveDraftCard(
            draft(cardId = "card-nubank"),
            notification(app = "Banco Desconhecido"),
            CardEvidence(cards = listOf(itau)),
        )

        assertEquals("card-nubank", result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `an ambiguous issuer falls through to the rule's card`() {
        val result = resolveDraftCard(
            draft(cardId = "card-nubank"),
            notification(),
            CardEvidence(cards = listOf(itau, card("card-itau-2", "Itaú"))),
        )

        assertEquals("card-nubank", result.draft.cardId)
    }

    @Test
    fun `an unmapped last4 reports it and drops the rule's card`() {
        val result = resolveDraftCard(
            draft(cardId = "card-nubank"),
            notification(paymentHint = "final 9999"),
            CardEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertEquals("9999", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an unmapped last4 does not fall through to an issuer that would have matched`() {
        val result = resolveDraftCard(
            draft(),
            notification(paymentHint = "final 9999"),
            CardEvidence(cards = listOf(itau)),
        )

        assertEquals("9999", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `a last4 mapped to two cards is treated as unmapped`() {
        val result = resolveDraftCard(
            draft(cardId = "card-nubank"),
            notification(paymentHint = "final 3685"),
            CardEvidence(last4Map = mapOf("card-a" to "3685", "card-b" to "3685")),
        )

        assertEquals("3685", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an income draft gets no card even with a matching last4`() {
        val result = resolveDraftCard(
            draft(method = null, type = TransactionType.INCOME),
            notification(paymentHint = "final 3685"),
            CardEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertNull(result.draft.cardId)
        assertNull(result.draft.paymentMethod)
    }

    @Test
    fun `an issuer name match never promotes a debit draft to credit`() {
        val result = resolveDraftCard(
            draft(method = PaymentMethod.DEBIT),
            notification(),
            CardEvidence(cards = listOf(itau)),
        )

        assertEquals(PaymentMethod.DEBIT, result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an issuer name match never promotes a pix draft to credit`() {
        val result = resolveDraftCard(
            draft(method = PaymentMethod.PIX),
            notification(),
            CardEvidence(cards = listOf(itau)),
        )

        assertEquals(PaymentMethod.PIX, result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }
}
