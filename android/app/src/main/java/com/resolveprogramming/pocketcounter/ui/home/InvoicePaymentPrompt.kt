package com.resolveprogramming.pocketcounter.ui.home

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem

/**
 * An invoice-payment push the matcher could not resolve to exactly one pending invoice. It offers a
 * picker or a dismissal and nothing else: both "Confirmar" and "Revisar" end in creating a new
 * expense on top of the invoice the user already tracks, which is the bug this surface exists to fix.
 */
data class InvoicePaymentPrompt(
    val notification: NotificationItem,
    /** Pending invoices the picker can offer; empty when nothing pending could be settled. */
    val candidates: List<HistoryItem>,
) {
    val notificationId: String get() = notification.id
}

/** The open picker sheet. [errorMessage] keeps a failed `markPaid` visible without losing the selection. */
data class InvoicePickerState(
    val prompt: InvoicePaymentPrompt,
    val isConfirming: Boolean = false,
    val errorMessage: String? = null,
)
