package com.resolveprogramming.pocketcounter.domain.usecase

import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import javax.inject.Inject

/**
 * The shared "turn a classified notification into a confirmed transaction" core, used by both the
 * wizard's final save and Home's one-tap confirm.
 *
 * Two paths:
 *  - [pendingTransactionId] != null → the notification matches an existing PENDING transaction; mark
 *    that transaction paid (no new row is created) and return its id.
 *  - otherwise → create a new transaction from [draft] and return the new id.
 *
 * In both cases the notification is then linked to the transaction via `markClassified` so it leaves
 * the review queue. That link is **best-effort**: the transaction is the source of truth and is never
 * rolled back if the link call fails (mirrors the wizard's long-standing semantics).
 *
 * This use case deliberately does NOT link recurring series or learn classification rules — those are
 * wizard-only concerns and must not run on a one-tap confirm.
 */
class ConfirmClassifiedNotificationUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val notificationRepository: NotificationRepository,
) {
    suspend operator fun invoke(
        notificationId: String,
        draft: WizardDraft,
        pendingTransactionId: String?,
    ): Result<String> {
        val result = if (pendingTransactionId != null) {
            transactionRepository.markPaid(pendingTransactionId).map { pendingTransactionId }
        } else {
            transactionRepository.save(draft)
        }
        return result.onSuccess { transactionId ->
            runCatching { notificationRepository.markClassified(notificationId, transactionId) }
        }
    }
}
