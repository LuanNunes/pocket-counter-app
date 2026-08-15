package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.InvoicePaymentMatch
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType

/**
 * Matches an issuer's invoice-payment confirmation push (see [InvoicePaymentDetector]) against the
 * user's pending credit-card invoices. Amount is the primary key; the issuer resolved via
 * [IssuerCardMatcher] only narrows an ambiguous amount match or vetoes a wrong one, never
 * substitutes for it. "Fatura" in PT-BR also means a utility/telco/insurer bill (Enel, Sabesp,
 * Vivo, Claro…), so without a cent-exact invoice match this only counts as a credit-card payment
 * when the issuer resolves to a tracked card — otherwise null, left for the revisar banner.
 */
fun matchInvoicePayment(
    notification: NotificationItem,
    pendingRows: List<HistoryItem>,
    cards: Collection<CreditCard>,
    learnedIssuers: Map<String, String>,
): InvoicePaymentMatch? {
    if (!InvoicePaymentDetector.isInvoicePaymentText(notification.text)) return null

    val amount = notification.parsed.amount
    val eligible = pendingRows.filter {
        it.statusPayment == PaymentStatus.PENDING && it.type == TransactionType.EXPENSE
    }
    val invoices = eligible.filter { it.isInvoice }
    val exact = invoices.filter { it.matchesInvoiceAmount(amount) }
    val issuerCardId = IssuerCardMatcher.resolve(notification.app, notification.text, cards, learnedIssuers)
    val viaLearnedIssuer = IssuerCardMatcher.isLearned(notification.app, learnedIssuers)

    if (exact.isNotEmpty()) return resolveByAmount(exact, issuerCardId, viaLearnedIssuer, invoices)
    if (issuerCardId == null) return null
    return fallbackToPicker(invoices)
}

private fun resolveByAmount(
    exact: List<HistoryItem>,
    issuerCardId: String?,
    viaLearnedIssuer: Boolean,
    invoices: List<HistoryItem>,
): InvoicePaymentMatch {
    if (exact.size == 1) {
        val row = exact.single()
        if (issuerCardId == null || issuerCardId == row.cardId) {
            return InvoicePaymentMatch.Matched(row, viaLearnedIssuer)
        }
        return InvoicePaymentMatch.NeedsChoice(invoices)
    }
    val narrowed = issuerCardId?.let { id -> exact.filter { it.cardId == id } } ?: exact
    return narrowed.singleOrNull()?.let { InvoicePaymentMatch.Matched(it, viaLearnedIssuer) }
        ?: InvoicePaymentMatch.NeedsChoice(invoices)
}

private fun fallbackToPicker(invoices: List<HistoryItem>): InvoicePaymentMatch =
    invoices.ifEmpty { null }?.let { InvoicePaymentMatch.NeedsChoice(it) } ?: InvoicePaymentMatch.NoCandidates
