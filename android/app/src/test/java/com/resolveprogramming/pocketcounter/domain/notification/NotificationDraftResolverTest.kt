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
        type: TransactionType = TransactionType.EXPENSE,
        text: String = "Compra no crédito R$ 49,90",
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
        suggestions = ClassificationSuggestion(),
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
    fun `a last4 hit names the card`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 3685"),
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
    fun `no card is set when neither last4 nor issuer resolves`() {
        val result = resolveDraftFromNotification(
            notification(app = "Banco Desconhecido"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertNull(result.draft.cardId)
        assertNull(result.unknownLast4)
    }

    @Test
    fun `an ambiguous issuer leaves the card unset`() {
        val result = resolveDraftFromNotification(
            notification(),
            NotificationEvidence(cards = listOf(itau, card("card-itau-2", "Itaú"))),
        )

        assertNull(result.draft.cardId)
    }

    @Test
    fun `an unmapped last4 reports it and sets no card`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 9999"),
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
            notification(paymentHint = "final 3685"),
            NotificationEvidence(last4Map = mapOf("card-a" to "3685", "card-b" to "3685")),
        )

        assertEquals("3685", result.unknownLast4)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an income draft gets no card even with a matching last4`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "final 3685", type = TransactionType.INCOME, text = "Salário R$ 49,90"),
            NotificationEvidence(last4Map = mapOf("card-itau" to "3685")),
        )

        assertNull(result.draft.cardId)
        assertNull(result.draft.paymentMethod)
    }

    @Test
    fun `an issuer name match never promotes a debit draft to credit`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra no débito R$ 49,90"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertEquals(PaymentMethod.DEBIT, result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `an issuer name match never promotes a pix draft to credit`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra via pix R$ 49,90"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertEquals(PaymentMethod.PIX, result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `the method comes from the built-in word list from the text`() {
        val methods = listOf("crédito", "débito", "pix").map { word ->
            resolveDraftFromNotification(
                notification(text = "Compra no $word R$ 49,90"),
                NotificationEvidence(),
            ).draft.paymentMethod
        }

        assertEquals(listOf(PaymentMethod.CREDIT, PaymentMethod.DEBIT, PaymentMethod.PIX), methods)
    }

    @Test
    fun `the method comes from the learned dictionary`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra aprovada MERCADO R$ 49,90"),
            NotificationEvidence(paymentMethodDictionary = mapOf("mercado" to PaymentMethod.CASH)),
        )

        assertEquals(PaymentMethod.CASH, result.draft.paymentMethod)
    }

    @Test
    fun `the learned dictionary beats the built-in word list`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra no débito R$ 49,90"),
            NotificationEvidence(paymentMethodDictionary = mapOf("débito" to PaymentMethod.PIX)),
        )

        assertEquals(PaymentMethod.PIX, result.draft.paymentMethod)
    }

    @Test
    fun `an income push mentioning crédito keeps no payment method`() {
        val result = resolveDraftFromNotification(
            notification(type = TransactionType.INCOME, text = "Crédito em conta R$ 49,90"),
            NotificationEvidence(cards = listOf(itau)),
        )

        assertNull(result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }

    @Test
    fun `a text-derived credit picks up the issuer card`() {
        val result = resolveDraftFromNotification(
            notification(),
            NotificationEvidence(cards = listOf(itau, nubank)),
        )

        assertEquals(PaymentMethod.CREDIT, result.draft.paymentMethod)
        assertEquals("card-itau", result.draft.cardId)
    }

    // -------------------------------------------------------------------------
    // The prefill chain's lower tiers: the server's hint, then a sole enabled method
    // -------------------------------------------------------------------------

    @Test
    fun `the server's payment hint names the method when the text does not`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "no crédito", text = "Compra aprovada MERCADO R$ 49,90"),
            NotificationEvidence(),
        )

        assertEquals(PaymentMethod.CREDIT, result.draft.paymentMethod)
    }

    @Test
    fun `the text wins over the server's payment hint`() {
        val result = resolveDraftFromNotification(
            notification(paymentHint = "no crédito", text = "Compra no débito R$ 49,90"),
            NotificationEvidence(),
        )

        assertEquals(PaymentMethod.DEBIT, result.draft.paymentMethod)
    }

    @Test
    fun `the only enabled method is prefilled when nothing else resolves`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra aprovada MERCADO R$ 49,90"),
            NotificationEvidence(enabledMethods = setOf(PaymentMethod.PIX)),
        )

        assertEquals(PaymentMethod.PIX, result.draft.paymentMethod)
    }

    @Test
    fun `more than one enabled method prefills nothing`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra aprovada MERCADO R$ 49,90"),
            NotificationEvidence(enabledMethods = setOf(PaymentMethod.PIX, PaymentMethod.CASH)),
        )

        assertNull(result.draft.paymentMethod)
    }

    @Test
    fun `a sole enabled method never beats evidence from the text`() {
        val result = resolveDraftFromNotification(
            notification(text = "Compra no débito R$ 49,90"),
            NotificationEvidence(enabledMethods = setOf(PaymentMethod.PIX)),
        )

        assertEquals(PaymentMethod.DEBIT, result.draft.paymentMethod)
    }

    @Test
    fun `an income draft is not prefilled with credit even when it is the only method enabled`() {
        val result = resolveDraftFromNotification(
            notification(type = TransactionType.INCOME, text = "Recebido MERCADO R$ 49,90"),
            NotificationEvidence(enabledMethods = setOf(PaymentMethod.CREDIT)),
        )

        assertNull(result.draft.paymentMethod)
        assertNull(result.draft.cardId)
    }
}
