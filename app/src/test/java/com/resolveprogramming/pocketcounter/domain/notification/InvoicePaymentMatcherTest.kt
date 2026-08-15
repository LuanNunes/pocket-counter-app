package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.InvoicePaymentMatch
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [matchInvoicePayment]. Amount is the primary key; the issuer only narrows or
 * vetoes — two signals contradicting each other must never resolve silently (see the
 * issuer-names-a-different-card case).
 */
class InvoicePaymentMatcherTest {

    private fun notification(
        text: String = "Nubank Recebemos seu pagamento no valor de R\$ 8.866,19. Obrigado!",
        app: String = "Nubank",
        amount: BigDecimal? = BigDecimal("8866.19"),
    ) = NotificationItem(
        id = "n1",
        app = app,
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        text = text,
        status = NotificationStatus.NEEDS_REVIEW,
        parsed = ParsedNotification(
            type = null,
            amount = amount,
            date = LocalDate.of(2026, 6, 29),
            merchantRaw = null,
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(tagIds = emptyList()),
        tokens = emptyList(),
    )

    private fun invoiceRow(
        id: String,
        amount: BigDecimal,
        cardId: String? = "card-1",
        statusPayment: PaymentStatus = PaymentStatus.PENDING,
        isInvoice: Boolean = true,
    ) = HistoryItem(
        id = id,
        date = LocalDate.of(2026, 6, 10),
        amount = amount,
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = statusPayment,
        cardId = cardId,
        isInvoice = isInvoice,
    )

    @Test
    fun `non-invoice-shape text returns null`() {
        val result = matchInvoicePayment(
            notification = notification(text = "Compra aprovada R\$ 30,00"),
            pendingRows = emptyList(),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `single exact amount match with no issuer conflict is Matched`() {
        val invoice = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"))

        val result = matchInvoicePayment(
            notification = notification(app = "unknown app"),
            pendingRows = listOf(invoice),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.Matched)
        assertEquals(invoice, (result as InvoicePaymentMatch.Matched).invoice)
    }

    @Test
    fun `single exact amount match but issuer names a different card is NeedsChoice`() {
        val invoice = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-2")
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
        )

        val result = matchInvoicePayment(
            notification = notification(app = "Nubank"),
            pendingRows = listOf(invoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.NeedsChoice)
        assertEquals(listOf(invoice), (result as InvoicePaymentMatch.NeedsChoice).candidates)
    }

    @Test
    fun `two exact amount matches narrowed to one by issuer is Matched`() {
        val nubankInvoice = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-1")
        val itauInvoice = invoiceRow(id = "tx-2", amount = BigDecimal("8866.19"), cardId = "card-2")
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
            CreditCard("card-2", "Itaú", "Visa", "1111", 0L, 0L, BigDecimal("2000"), 5),
        )

        val result = matchInvoicePayment(
            notification = notification(app = "Nubank"),
            pendingRows = listOf(nubankInvoice, itauInvoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.Matched)
        assertEquals(nubankInvoice, (result as InvoicePaymentMatch.Matched).invoice)
    }

    @Test
    fun `a match narrowed by a learned issuer entry is flagged viaLearnedIssuer`() {
        val nubankInvoice = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-1")
        val itauInvoice = invoiceRow(id = "tx-2", amount = BigDecimal("8866.19"), cardId = "card-2")

        val result = matchInvoicePayment(
            notification = notification(app = "Nubank"),
            pendingRows = listOf(nubankInvoice, itauInvoice),
            cards = emptyList(),
            learnedIssuers = mapOf("nubank" to "card-1"),
        )

        assertTrue(result is InvoicePaymentMatch.Matched)
        assertTrue((result as InvoicePaymentMatch.Matched).viaLearnedIssuer)
    }

    @Test
    fun `a match resolved purely by amount, with no card on file for the issuer, is not flagged viaLearnedIssuer`() {
        val invoice = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"))

        val result = matchInvoicePayment(
            notification = notification(app = "unknown app"),
            pendingRows = listOf(invoice),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.Matched)
        assertFalse((result as InvoicePaymentMatch.Matched).viaLearnedIssuer)
    }

    @Test
    fun `issuer names a card with no same-amount invoice narrows to zero and stays NeedsChoice`() {
        val nubankInvoice = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-1")
        val itauInvoice = invoiceRow(id = "tx-2", amount = BigDecimal("8866.19"), cardId = "card-2")
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
            CreditCard("card-2", "Itaú", "Visa", "1111", 0L, 0L, BigDecimal("2000"), 5),
            CreditCard("card-3", "C6", "Mastercard", "2222", 0L, 0L, BigDecimal("500"), 1),
        )

        val result = matchInvoicePayment(
            notification = notification(app = "C6"),
            pendingRows = listOf(nubankInvoice, itauInvoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.NeedsChoice)
        assertEquals(listOf(nubankInvoice, itauInvoice), (result as InvoicePaymentMatch.NeedsChoice).candidates)
    }

    @Test
    fun `issuer narrows three exact matches down to two and stays NeedsChoice`() {
        val nubankInvoiceA = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-1")
        val nubankInvoiceB = invoiceRow(id = "tx-2", amount = BigDecimal("8866.19"), cardId = "card-1")
        val itauInvoice = invoiceRow(id = "tx-3", amount = BigDecimal("8866.19"), cardId = "card-2")
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
            CreditCard("card-2", "Itaú", "Visa", "1111", 0L, 0L, BigDecimal("2000"), 5),
        )

        val result = matchInvoicePayment(
            notification = notification(app = "Nubank"),
            pendingRows = listOf(nubankInvoiceA, nubankInvoiceB, itauInvoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.NeedsChoice)
        assertEquals(
            listOf(nubankInvoiceA, nubankInvoiceB, itauInvoice),
            (result as InvoicePaymentMatch.NeedsChoice).candidates,
        )
    }

    @Test
    fun `two exact amount matches with no issuer resolution is NeedsChoice`() {
        val invoiceA = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-1")
        val invoiceB = invoiceRow(id = "tx-2", amount = BigDecimal("8866.19"), cardId = "card-2")

        val result = matchInvoicePayment(
            notification = notification(app = "unknown app"),
            pendingRows = listOf(invoiceA, invoiceB),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.NeedsChoice)
        assertEquals(listOf(invoiceA, invoiceB), (result as InvoicePaymentMatch.NeedsChoice).candidates)
    }

    @Test
    fun `an ambiguous exact match also carries other pending invoices at different amounts`() {
        val invoiceA = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), cardId = "card-1")
        val invoiceB = invoiceRow(id = "tx-2", amount = BigDecimal("8866.19"), cardId = "card-2")
        val otherInvoice = invoiceRow(id = "tx-3", amount = BigDecimal("250.00"), cardId = "card-3")

        val result = matchInvoicePayment(
            notification = notification(app = "unknown app"),
            pendingRows = listOf(invoiceA, invoiceB, otherInvoice),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.NeedsChoice)
        assertEquals(
            listOf(invoiceA, invoiceB, otherInvoice),
            (result as InvoicePaymentMatch.NeedsChoice).candidates,
        )
    }

    @Test
    fun `no exact match and no card on file for the issuer returns null, not ours to touch`() {
        val result = matchInvoicePayment(
            notification = notification(),
            pendingRows = emptyList(),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `no exact match with a known card for the issuer falls back to pending invoices, excluding plain pending expenses`() {
        val plainExpense = invoiceRow(id = "tx-plain", amount = BigDecimal("50.00"), isInvoice = false)
        val otherInvoice = invoiceRow(id = "tx-invoice", amount = BigDecimal("120.00"), isInvoice = true)
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
        )

        val result = matchInvoicePayment(
            notification = notification(),
            pendingRows = listOf(plainExpense, otherInvoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.NeedsChoice)
        assertEquals(listOf(otherInvoice), (result as InvoicePaymentMatch.NeedsChoice).candidates)
    }

    @Test
    fun `a bill push from an app with no matching card and no cent-exact invoice returns null`() {
        // Regression for the "fatura" false positive: Enel/Sabesp/Vivo/Claro all push text
        // matching InvoicePaymentDetector's phrases, but none of them is a tracked credit card.
        val invoice = invoiceRow(id = "tx-1", amount = BigDecimal("250.00"), cardId = "card-1")
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
        )

        val result = matchInvoicePayment(
            notification = notification(
                text = "Vivo Recebemos o pagamento da sua fatura no valor de R\$ 89,90. Obrigado!",
                app = "Vivo",
                amount = BigDecimal("89.90"),
            ),
            pendingRows = listOf(invoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertNull(result)
    }

    @Test
    fun `PAID rows are excluded from both the exact match and the picker`() {
        val paidInvoice = invoiceRow(
            id = "tx-paid",
            amount = BigDecimal("8866.19"),
            statusPayment = PaymentStatus.PAID,
        )
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
        )

        val result = matchInvoicePayment(
            notification = notification(),
            pendingRows = listOf(paidInvoice),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertEquals(InvoicePaymentMatch.NoCandidates, result)
    }

    @Test
    fun `a non-invoice row with a matching amount is excluded from both the exact match and the picker`() {
        val nonInvoiceRow = invoiceRow(id = "tx-1", amount = BigDecimal("8866.19"), isInvoice = false)
        val cards = listOf(
            CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
        )

        val result = matchInvoicePayment(
            notification = notification(),
            pendingRows = listOf(nonInvoiceRow),
            cards = cards,
            learnedIssuers = emptyMap(),
        )

        assertEquals(InvoicePaymentMatch.NoCandidates, result)
    }

    @Test
    fun `amounts with different scale but equal value still match exactly`() {
        // BigDecimal("8886.9").equals(BigDecimal("8886.90")) is false — real API payloads can
        // legitimately differ in scale, so comparison must use compareTo, never equals.
        val invoice = invoiceRow(id = "tx-1", amount = BigDecimal("8886.9"))

        val result = matchInvoicePayment(
            notification = notification(amount = BigDecimal("8886.90")),
            pendingRows = listOf(invoice),
            cards = emptyList(),
            learnedIssuers = emptyMap(),
        )

        assertTrue(result is InvoicePaymentMatch.Matched)
        assertEquals(invoice, (result as InvoicePaymentMatch.Matched).invoice)
    }
}
