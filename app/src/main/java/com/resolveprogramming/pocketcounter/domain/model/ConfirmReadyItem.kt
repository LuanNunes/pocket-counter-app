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
)
