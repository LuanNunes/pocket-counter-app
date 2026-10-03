package com.resolveprogramming.pocketcounter.ui.home.components

import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.ui.components.LedgerLookups
import com.resolveprogramming.pocketcounter.ui.home.InvoicePaymentPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class InvoicePaymentPresentationTest {

    private val today = LocalDate.of(2026, 8, 5)

    private val lookups = LedgerLookups(
        cards = mapOf(
            "card-nu" to CreditCard("card-nu", "Nubank", "MASTER", "1234", 0L, 0L, BigDecimal.ZERO, 10),
            "card-itau" to CreditCard("card-itau", "Itaú", "VISA", "5678", 0L, 0L, BigDecimal.ZERO, 15),
        ),
        tags = emptyMap(),
        contexts = emptyMap(),
    )

    private fun notification(amount: BigDecimal? = BigDecimal("8866.19")) = NotificationItem(
        id = "n1",
        app = "Nubank",
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-08-05T13:25:00Z",
        text = "Nubank Recebemos seu pagamento no valor de R$ 8.866,19. Obrigado!",
        status = NotificationStatus.NEEDS_REVIEW,
        parsed = ParsedNotification(type = null, amount = amount, date = today, merchantRaw = null, paymentHint = null),
        suggestions = ClassificationSuggestion(tagIds = emptyList()),
        tokens = emptyList(),
    )

    private fun invoice(
        id: String,
        amount: String,
        name: String,
        cardId: String = "card-nu",
        date: LocalDate = LocalDate.of(2026, 8, 10),
        isInvoice: Boolean = true,
    ) = HistoryItem(
        id = id,
        date = date,
        amount = BigDecimal(amount),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PENDING,
        paymentMethod = PaymentMethod.CREDIT,
        cardId = cardId,
        name = name,
        isInvoice = isInvoice,
    )

    private fun prompt(vararg candidates: HistoryItem, amount: BigDecimal? = BigDecimal("8866.19")) = InvoicePaymentPrompt(
        notification = notification(amount),
        candidates = candidates.toList(),
    )

    // -- reason line --

    @Test
    fun `several invoices with the same amount name the ambiguity`() {
        val presentation = invoicePromptPresentation(
            prompt(
                invoice("i1", "-8866.19", "Fatura Nubank"),
                invoice("i2", "-8866.19", "Fatura Nubank anterior"),
            ),
        )

        assertEquals(
            InvoiceReasonLine.Plain("Mais de uma fatura pendente tem esse valor."),
            presentation.reason,
        )
        assertTrue(presentation.canChoose)
    }

    @Test
    fun `an amount matching one card while the push names another states both signals`() {
        val presentation = invoicePromptPresentation(
            prompt(invoice("i1", "-8866.19", "Fatura Itaú", cardId = "card-itau")),
        )

        assertEquals(InvoiceReasonLine.Conflict("Fatura Itaú", "Nubank"), presentation.reason)
        assertEquals(
            "O valor bate com a Fatura Itaú, mas a notificação é do Nubank.",
            presentation.reason.spoken,
        )
    }

    @Test
    fun `no amount match but pending invoices exist keeps the picker`() {
        val presentation = invoicePromptPresentation(prompt(invoice("i1", "-4000.00", "Fatura Nubank")))

        assertEquals(
            InvoiceReasonLine.Plain("Nenhuma fatura pendente com esse valor."),
            presentation.reason,
        )
        assertTrue(presentation.canChoose)
    }

    @Test
    fun `nothing pending drops the picker entirely`() {
        val presentation = invoicePromptPresentation(prompt())

        assertEquals(
            InvoiceReasonLine.Plain("Nenhuma fatura pendente para marcar como paga."),
            presentation.reason,
        )
        assertFalse(presentation.canChoose)
    }

    @Test
    fun `a same-amount plain expense is not treated as a conflicting invoice`() {
        val presentation = invoicePromptPresentation(
            prompt(invoice("e1", "-8866.19", "Aluguel", isInvoice = false)),
        )

        assertEquals(
            InvoiceReasonLine.Plain("Nenhuma fatura pendente com esse valor."),
            presentation.reason,
        )
    }

    @Test
    fun `the amount is carried apart from the meta line so the card can emphasise it`() {
        val presentation = invoicePromptPresentation(prompt())

        // Separate field, not folded into the meta string: it is the card's identifying fact, and
        // the meta line renders in the smallest, dimmest style.
        assertEquals(BigDecimal("8866.19"), presentation.amount)
        assertFalse(presentation.metaLine.contains("8.866,19"))
        assertTrue(presentation.metaLine.contains("Nubank"))
        assertTrue(presentation.metaLine.endsWith("agora"))
    }

    @Test
    fun `the spoken description still carries the amount the card shows visually`() {
        val presentation = invoicePromptPresentation(prompt())

        assertTrue(presentation.contentDescription.contains("8.866,19"))
    }

    // -- picker --

    @Test
    fun `groups are labelled only when both of them have rows`() {
        val presentation = invoicePickerPresentation(
            prompt(
                invoice("i1", "-8866.19", "Fatura Nubank"),
                invoice("i2", "-5906.91", "Fatura Itaú", cardId = "card-itau"),
            ),
            lookups,
            today,
        )

        assertEquals(listOf("MESMO VALOR", "OUTRAS FATURAS PENDENTES"), presentation.groups.map { it.header })
        assertEquals(listOf("i1"), presentation.groups.first().rows.map { it.id })
    }

    @Test
    fun `one unlabelled list needs no header`() {
        val presentation = invoicePickerPresentation(
            prompt(invoice("i1", "-4000.00", "Fatura Nubank")),
            lookups,
            today,
        )

        assertEquals(listOf<String?>(null), presentation.groups.map { it.header })
    }

    @Test
    fun `a picker row is identified by its payment label and due date`() {
        val presentation = invoicePickerPresentation(
            prompt(invoice("i1", "-8886.92", "Fatura Nubank")),
            lookups,
            today,
        )

        val row = presentation.groups.single().rows.single()
        assertEquals("Fatura Nubank", row.title)
        assertEquals(BigDecimal("8886.92"), row.amount)
        assertEquals("Crédito Nubank · vence 10 ago", row.meta)
        assertTrue(row.contentDescription.contains("vence 10 de agosto"))
        assertTrue(row.contentDescription.contains("pendente"))
    }

    @Test
    fun `the picker subtitle repeats the payment so rows can be compared without leaving`() {
        val presentation = invoicePickerPresentation(prompt(), lookups, today)

        assertTrue(presentation.subtitle.startsWith("Pagamento de "))
        assertTrue(presentation.subtitle.endsWith("· Nubank"))
        assertTrue(presentation.groups.isEmpty())
        assertEquals(BigDecimal("8866.19"), presentation.referenceAmount)
        assertEquals("Nubank", presentation.issuer)
    }

    @Test
    fun `an unparsed notification amount drops the payment segment without a dangling separator`() {
        val presentation = invoicePickerPresentation(prompt(amount = null), lookups, today)

        assertEquals("Nubank", presentation.subtitle)
        assertNull(presentation.referenceAmount)
        assertEquals("Nubank", presentation.issuer)
    }

    // -- mismatch note --

    @Test
    fun `an equal amount produces no note`() {
        assertNull(invoiceMismatchNote(BigDecimal("8886.92"), invoice("i1", "-8886.92", "Fatura Nubank")))
    }

    @Test
    fun `a partial payment states that the whole invoice is settled`() {
        val note = invoiceMismatchNote(BigDecimal("4000.00"), invoice("i1", "-8886.92", "Fatura Nubank"))

        assertEquals("a fatura inteira como paga", note?.emphasis)
        assertTrue(note!!.spoken.startsWith("A notificação diz "))
        assertTrue(note.spoken.contains("Se você pagou só uma parte, confirmar marca a fatura inteira como paga."))
    }

    @Test
    fun `an overpayment asks for confirmation without bolding a consequence`() {
        val note = invoiceMismatchNote(BigDecimal("9100.00"), invoice("i1", "-8886.92", "Fatura Nubank"))

        assertNull(note?.emphasis)
        assertTrue(note!!.spoken.endsWith("Confirme só se for esta a fatura paga."))
    }

    @Test
    fun `an unparsed notification amount produces no note`() {
        assertNull(invoiceMismatchNote(null, invoice("i1", "-8886.92", "Fatura Nubank")))
    }
}
