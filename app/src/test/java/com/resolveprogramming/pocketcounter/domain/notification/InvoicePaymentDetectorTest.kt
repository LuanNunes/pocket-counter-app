package com.resolveprogramming.pocketcounter.domain.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [InvoicePaymentDetector].
 *
 * Phrase-level matching only — a word-level match would swallow "você pagou" and every boleto
 * push, which must keep classifying as a regular EXPENSE.
 */
class InvoicePaymentDetectorTest {

    @Test
    fun `recebemos seu pagamento phrase is an invoice payment`() {
        assertTrue(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Nubank Recebemos seu pagamento no valor de R$ 8.866,19. Obrigado!",
            ),
        )
    }

    @Test
    fun `recebemos o pagamento phrase is an invoice payment`() {
        assertTrue(
            InvoicePaymentDetector.isInvoicePaymentText("Recebemos o pagamento da sua fatura Itaú."),
        )
    }

    @Test
    fun `pagamento da fatura phrase is an invoice payment`() {
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("Confirmamos o pagamento da fatura."))
    }

    @Test
    fun `pagamento da sua fatura phrase is an invoice payment`() {
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("Identificamos o pagamento da sua fatura."))
    }

    @Test
    fun `pagamento recebido phrase is an invoice payment`() {
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("Pagamento recebido com sucesso."))
    }

    @Test
    fun `fatura paga phrase is an invoice payment`() {
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("Sua fatura paga! Obrigado."))
    }

    @Test
    fun `phrase match is case-insensitive`() {
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("RECEBEMOS SEU PAGAMENTO no valor de R$ 10,00."))
    }

    @Test
    fun `phrase match is accent-insensitive`() {
        // None of the target phrases carry diacritics in standard PT-BR, but an issuer push
        // may still arrive with a stray accent (encoding artifact); folding must not miss it.
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("Recebêmos seu pagaménto no valor de R$ 10,00."))
    }

    @Test
    fun `pagamento aprovado is not an invoice payment`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("Pagamento aprovado R$ 120,00"))
    }

    @Test
    fun `voce pagou is not an invoice payment`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("Você pagou R$ 50,00"))
    }

    @Test
    fun `pix enviado is not an invoice payment`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("Pix enviado no valor de R$ 30,00"))
    }

    @Test
    fun `agende o pagamento da fatura is not an invoice payment`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("Agende o pagamento da fatura"))
    }

    @Test
    fun `antecipe o pagamento da fatura is not an invoice payment`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("Antecipe o pagamento da fatura"))
    }

    @Test
    fun `nao identificamos o pagamento is not an invoice payment, even though the verb is adjacent`() {
        // The naive "verb adjacent to the phrase" rule still matches here — the negation precedes
        // the verb, not the phrase, so it must be checked explicitly.
        assertFalse(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Não identificamos o pagamento da sua fatura de R$ 8.886,92",
            ),
        )
    }

    @Test
    fun `ainda nao recebemos o pagamento is not an invoice payment, even though the phrase is present`() {
        // The negation veto must reach every PHRASES entry, not just the CONFIRMATION_REGEX branch.
        assertFalse(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Ainda não recebemos o pagamento da sua fatura. Regularize hoje.",
            ),
        )
    }

    @Test
    fun `ainda nao recebemos seu pagamento is not an invoice payment`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("Ainda não recebemos seu pagamento"))
    }

    @Test
    fun `nao recebemos seu pagamento is not an invoice payment when uppercase`() {
        assertFalse(InvoicePaymentDetector.isInvoicePaymentText("NÃO RECEBEMOS SEU PAGAMENTO"))
    }

    @Test
    fun `nao identificamos with a double space before the verb is not an invoice payment`() {
        // A wrapped/encoded push can carry more than one whitespace character between the words.
        assertFalse(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Não  identificamos o pagamento da sua fatura de R$ 8.886,92",
            ),
        )
    }

    @Test
    fun `nao identificamos across a newline is not an invoice payment`() {
        // PocketNotificationListenerService joins title + EXTRA_BIG_TEXT, and the body can wrap.
        assertFalse(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Não\nidentificamos o pagamento da sua fatura de R$ 8.886,92",
            ),
        )
    }

    @Test
    fun `recebemos seu pagamento followed by a trailing nao clause is still an invoice payment`() {
        assertTrue(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Recebemos seu pagamento. Não é necessário fazer mais nada.",
            ),
        )
    }

    @Test
    fun `fatura paga followed by a trailing nao clause is still an invoice payment`() {
        assertTrue(InvoicePaymentDetector.isInvoicePaymentText("Fatura paga. Não há pendências."))
    }

    @Test
    fun `recebemos o pagamento da fatura followed by a trailing nao clause is still an invoice payment`() {
        assertTrue(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Recebemos o pagamento da sua fatura. Não envie o comprovante novamente.",
            ),
        )
    }

    @Test
    fun `standard bank boilerplate nao reconheca clause after the phrase is still an invoice payment`() {
        assertTrue(
            InvoicePaymentDetector.isInvoicePaymentText(
                "Recebemos seu pagamento de R$ 100,00. Caso não reconheça, ligue para a central.",
            ),
        )
    }
}
