package com.resolveprogramming.pocketcounter.domain.model

/**
 * Outcome of matching an issuer's invoice-payment confirmation push against the user's pending
 * credit-card invoices. See [com.resolveprogramming.pocketcounter.domain.notification.matchInvoicePayment].
 */
sealed interface InvoicePaymentMatch {
    /**
     * @param viaLearnedIssuer True when a previously-taught issuer→card mapping (not the app/text
     * name, not the amount alone) is what resolved [invoice]. Dismissing a match like this is the
     * only user signal that the taught mapping itself is wrong, as opposed to a match the learned
     * map had no part in.
     */
    data class Matched(val invoice: HistoryItem, val viaLearnedIssuer: Boolean = false) : InvoicePaymentMatch
    data class NeedsChoice(val candidates: List<HistoryItem>) : InvoicePaymentMatch
    data object NoCandidates : InvoicePaymentMatch
}
