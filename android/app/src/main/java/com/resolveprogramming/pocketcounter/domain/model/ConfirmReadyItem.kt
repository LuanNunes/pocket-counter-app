package com.resolveprogramming.pocketcounter.domain.model

/**
 * A pending notification the classifier recognized confidently enough to confirm in one tap, without
 * walking the wizard. Carries the fully-built [draft] (so confirming just saves it) plus the
 * [pendingTransactionId] when the notification matches an existing PENDING transaction (confirm then
 * means "mark that transaction paid", not "create a new one"). [notification] is retained for display
 * (raw text / received time) and to re-open the wizard on the "Revisar" fallback.
 */
data class ConfirmReadyItem(
    val notificationId: String,
    val draft: WizardDraft,
    val pendingTransactionId: String?,
    val notification: NotificationItem,
    /**
     * The matched row itself, when the client resolved it (the invoice-payment path). Every number
     * on a card that authorizes a write must describe the row that changes, not the notification.
     */
    val pendingMatch: HistoryItem? = null,
    /** True when [pendingMatch] came from a taught issuer→card mapping — see [InvoicePaymentMatch.Matched]. */
    val viaLearnedIssuer: Boolean = false,
)
