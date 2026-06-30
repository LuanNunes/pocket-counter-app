package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.ConfirmReadyItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft

/**
 * Decides whether a classified notification can be confirmed in one tap (bypassing the wizard) and, if
 * so, projects it into a [ConfirmReadyItem]. Returns null when the notification still needs the wizard.
 *
 * Eligible when either:
 *  - it matches an existing PENDING transaction ([ClassifiedNotification.pendingTransactionId] != null) —
 *    confirming marks that transaction paid; or
 *  - the backend classified it as [NotificationStatus.AUTO] AND the built draft is actually saveable
 *    (steps 1–3 valid). The status alone isn't enough: an AUTO credit suggestion can lack its cardId,
 *    which the wizard would normally force the user to pick — so we re-validate and fall back to the
 *    wizard rather than create an invalid transaction.
 *
 * NEEDS_TAGS / NEEDS_REVIEW are never confirm-ready.
 */
fun confirmReadyItemOf(classified: ClassifiedNotification): ConfirmReadyItem? {
    val draft = WizardDraft.fromNotification(classified.notification)
    if (!isConfirmReady(classified, draft)) return null
    return ConfirmReadyItem(
        notificationId = classified.notification.id,
        draft = draft,
        pendingTransactionId = classified.pendingTransactionId,
        notification = classified.notification,
    )
}

private fun isConfirmReady(classified: ClassifiedNotification, draft: WizardDraft): Boolean {
    if (classified.pendingTransactionId != null) return true
    if (classified.notification.status != NotificationStatus.AUTO) return false
    return draft.isStep1Valid() && draft.isStep2Valid() && draft.isStep3Valid()
}
