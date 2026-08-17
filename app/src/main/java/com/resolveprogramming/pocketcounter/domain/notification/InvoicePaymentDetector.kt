package com.resolveprogramming.pocketcounter.domain.notification

import java.text.Normalizer

/**
 * Read-side detector for invoice-payment confirmation pushes ("Recebemos seu pagamento...").
 *
 * These texts must NOT classify as a plain EXPENSE — see [BrNotificationParser.parseType]'s
 * guard, which calls this before any keyword scan. Phrase-level matching only: a word-level
 * match on "pagamento" would swallow "você pagou" and every boleto expense push.
 */
object InvoicePaymentDetector {

    /**
     * [hasResolvedIssuer] gates only the "pagamento recebido" phrase below — PagBank, Mercado Pago,
     * InfinitePay and SumUp all send that exact acquirer-receipt text for money coming IN, so alone
     * it needs either a resolved card issuer or a "fatura" mention to count. The other phrases name
     * an invoice unambiguously and are unaffected.
     */
    fun isInvoicePaymentText(text: String, hasResolvedIssuer: Boolean = false): Boolean {
        val folded = fold(text)
        if (UNAMBIGUOUS_PHRASES.any { matches(folded, it) }) return true
        if (matchesConfirmationRegex(folded)) return true
        if (!matches(folded, AMBIGUOUS_RECEIPT_PHRASE)) return false
        return hasResolvedIssuer || folded.contains("fatura")
    }

    private fun matches(folded: String, phrase: String): Boolean {
        val index = folded.indexOf(phrase)
        return index >= 0 && !isNegated(folded, index)
    }

    private fun matchesConfirmationRegex(folded: String): Boolean {
        val match = CONFIRMATION_REGEX.find(folded) ?: return false
        return !isNegated(folded, match.range.first)
    }

    /**
     * A "não"/"nao" in the [NEGATION_WINDOW] chars immediately before the phrase vetoes it — e.g.
     * "Ainda não recebemos o pagamento". Backward only: a "não" trailing the phrase ("Recebemos
     * seu pagamento. Não é necessário fazer mais nada.") is standard confirmation boilerplate, not
     * a negation of the confirmation itself, and must not veto it.
     */
    private fun isNegated(folded: String, start: Int): Boolean {
        val windowStart = (start - NEGATION_WINDOW).coerceAtLeast(0)
        return NEGATION_WORD_REGEX.containsMatchIn(folded.substring(windowStart, start))
    }

    private fun fold(text: String): String {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        return DIACRITIC_REGEX.replace(normalized, "")
    }

    private val DIACRITIC_REGEX = Regex("\\p{Mn}+")
    private val NEGATION_WORD_REGEX = Regex("""\bnao\b""")
    private const val NEGATION_WINDOW = 30

    private val UNAMBIGUOUS_PHRASES = listOf(
        "recebemos seu pagamento",
        "recebemos o pagamento",
        "fatura paga",
    )

    // Standard merchant-acquirer receipt language, not issuer language — see isInvoicePaymentText's
    // KDoc for the gate this needs.
    private const val AMBIGUOUS_RECEIPT_PHRASE = "pagamento recebido"

    // "pagamento da (sua) fatura" alone is the generic noun phrase for paying an invoice and shows up
    // in reminders/dunning pushes too ("Agende o pagamento da fatura"); it only means a confirmation
    // when a confirmation verb sits right next to it. [isNegated] rejects "não identificamos …",
    // where the verb IS adjacent to the phrase but the negation precedes the verb.
    private val CONFIRMATION_REGEX = Regex(
        "(recebemos|confirmamos|identificamos|registramos) o pagamento da (sua )?fatura",
    )
}
