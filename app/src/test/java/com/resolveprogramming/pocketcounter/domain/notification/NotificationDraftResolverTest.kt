package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class NotificationDraftResolverTest {

    private fun notification(
        paymentHint: String? = null,
        app: String = "Banco Itaú",
        method: PaymentMethod? = PaymentMethod.CREDIT,
        cardId: String? = null,
        type: TransactionType = TransactionType.EXPENSE,
        text: String = "Compra aprovada R$ 49,90",
    ) = NotificationItem(
        id = "n1",
        app = app,
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        text = text,
        status = NotificationStatus.AUTO,
        parsed = ParsedNotification(
            type = type,
            amount = BigDecimal("49.90"),
            date = LocalDate.of(2026, 6, 29),
            merchantRaw = "IFOOD",
            paymentHint = paymentHint,
        ),
        suggestions = ClassificationSuggestion(
            tagIds = emptyList(),
            paymentMethod = method,
            cardId = cardId,
        ),
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

    private val itau = card("card-itau", "Itaú")
    private val nubank = card("card-nubank", "Nubank")

    @Test
    fun `a last4 hit beats the card a rule provided`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 3685", cardId = "card-nubank"),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685", "card-nubank" to "1111")),
        )

        assertEquals("card-itau", result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `the issuer resolves the card when there is no last4 hint`() {
        val result = resolveDraftFromNotification(
            notification(),
            NotificationEvidence(cards = listOf(itau, nubank)),
        )

        assertEquals("card-itau", result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `the rule's card is kept when neither last4 nor issuer resolves`() {
        val result = resolveDraftFromNotification(
            notification(app = "Banco Desconhecido", cardId = "card-nubank"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertEquals("card-nubank", result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `an ambiguous issuer falls through to the rule's card`() {
        val result = resolveDraftFromNotification(
            notification(cardId = "card-nubank"),
            NotificationEvidence(cards = listOf(itau, card("card-itau-2", "Itaú"))),
        )

        assertEquals("card-nubank", result.draft.cardId)
    }

    @Test
    fun `an unmapped last4 reports it and drops the rule's card`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 9999", cardId = "card-nubank"),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertEquals("9999", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an unmapped last4 does not fall through to an issuer that would have matched`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 9999"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertEquals("9999", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `a last4 mapped to two cards is treated as unmapped`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 3685", cardId = "card-nubank"),
            NotificationEvidence(last4Map = mapOf("card-a" to "3685", "card-b" to "3685")),
        )

        assertEquals("3685", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an income draft gets no card even with a matching last4`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 3685", method = null, type = TransactionType.INCOME),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertNull(result.draft.cardId)
        assertNull(result.draft.paymentMethod)
    }

    @Test
    fun `an issuer name match never promotes a debit draft to credit`() {
        val result = resolveDraftFromNotification(
            notification(method = PaymentMethod.DEBIT),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertEquals(PaymentMethod.DEBIT, result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an issuer name match never promotes a pix draft to credit`() {
        val result = resolveDraftFromNotification(
            notification(method = PaymentMethod.PIX),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertEquals(PaymentMethod.PIX, result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `the method comes from the built-in word list when the rule supplied none`() {
        val methods = listOf("crédito", "débito", "pix").map { word ->
            resolveDraftFromNotification(
                notification(method = null, text = "Compra no $word R$ 49,90"),
                NotificationEvidence(),
            ).draft.paymentMethod
        }

        assertEquals(listOf(PaymentMethod.CREDIT, PaymentMethod.DEBIT, PaymentMethod.PIX), methods)
    }

    @Test
    fun `the method comes from the learned dictionary`() {
        val result = resolveDraftFromNotification(
            notification(method = null, text = "Compra aprovada MERCADO R$ 49,90"),
            NotificationEvidence(paymentMethodDictionary = mapOf("mercado" to PaymentMethod.CASH)),
        )

        assertEquals(PaymentMethod.CASH, result.draft.paymentMethod)
    }

    @Test
    fun `the learned dictionary beats the built-in word list`() {
        val result = resolveDraftFromNotification(
            notification(method = null, text = "Compra no débito R$ 49,90"),
            NotificationEvidence(paymentMethodDictionary = mapOf("débito" to PaymentMethod.PIX)),
        )

        assertEquals(PaymentMethod.PIX, result.draft.paymentMethod)
    }

    @Test
    fun `a rule-supplied method beats the dictionary and the word list`() {
        val result = resolveDraftFromNotification(
            notification(method = PaymentMethod.DEBIT, text = "Compra no crédito R$ 49,90"),
            NotificationEvidence(paymentMethodDictionary = mapOf("crédito" to PaymentMethod.PIX)),
        )

        assertEquals(PaymentMethod.DEBIT, result.draft.paymentMethod)
    }

    @Test
    fun `an income push mentioning crédito keeps no payment method`() {
        val result = resolveDraftFromNotification(
            notification(method = null, type = TransactionType.INCOME, text = "Crédito em conta R$ 49,90"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertNull(result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `a text-derived credit picks up the issuer card`() {
        val result = resolveDraftFromNotification(
            notification(method = null, text = "Compra no crédito R$ 49,90"),
            NotificationEvidence(cards = listOf(itau, nubank)),
        )

        assertEquals(PaymentMethod.CREDIT, result.draft.paymentMethod)
        assertEquals("card-itau", result.draft.cardId)
    }
}
