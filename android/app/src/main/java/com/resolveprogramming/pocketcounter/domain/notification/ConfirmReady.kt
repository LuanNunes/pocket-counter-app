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
 *    (steps 1–3 valid) with a title worth persisting ([WizardDraft.hasUsableName]). The status alone
 *    isn't enough: an AUTO credit suggestion can lack its cardId, and a merchant-less push would save
 *    a nameless row — both are things the wizard forces the user to resolve, so we re-validate and
 *    fall back to it rather than create a transaction the user has to go and repair.
 *
 * NEEDS_TAGS / NEEDS_REVIEW are never confirm-ready.
 *
 * [evidence] has no default on purpose: a call site that skipped it would silently file the charge
 * with whatever payment method and card the matched rule pinned, ignoring the push's own text.
 */
fun confirmReadyItemOf(classified: ClassifiedNotification, evidence: NotificationEvidence): ConfirmReadyItem? {
    val draft = resolveDraftFromNotification(classified.notification, evidence).draft
    if (!isConfirmReady(classified, draft)) return null
    return ConfirmReadyItem(
        notificationId = classified.notification.id,
        draft = draft,
        pendingTransactionId = classified.pendingTransactionId,
        notification = classified.notification,
    )
}

private fun isConfirmReady(classified: ClassifiedNotification, draft: WizardDraft): Boolean {
    // Settling a pending row calls markPaid and discards the draft, so its name is never written.
    if (classified.pendingTransactionId != null) return true
    if (classified.notification.status != NotificationStatus.AUTO) return false
    if (!draft.hasUsableName()) return false
    return draft.isStep1Valid() && draft.isStep2Valid() && draft.isStep3Valid()
}
