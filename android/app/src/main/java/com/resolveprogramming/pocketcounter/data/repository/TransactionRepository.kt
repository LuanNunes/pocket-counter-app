package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft

/**
 * A 409 on create: the backend already holds a matching row. [existingTransactionId] is the error
 * body's first detail, when it sent one.
 */
class DuplicateTransactionException(val existingTransactionId: String?) :
    RuntimeException("Transação duplicada")

interface TransactionRepository {
    /**
     * [notificationId] attributes the row to the notification it came from — see
     * `TransactionDto.idNotification`. [allowDuplicate] re-sends a create the backend refused with a
     * [DuplicateTransactionException], after the user confirmed it.
     */
    suspend fun save(
        draft: WizardDraft,
        notificationId: String? = null,
        allowDuplicate: Boolean = false,
    ): Result<String>
    suspend fun update(transactionId: String, draft: WizardDraft): Result<String>

    /** Sets a transaction's own tags. null = clear own tags; non-null = override with these tags. */
    suspend fun setTags(item: HistoryItem, tagIds: List<String>?): Result<Unit>
    /** Persists a manual order: each id's index becomes its displayOrder. */
    suspend fun reorder(orderedIds: List<String>): Result<Unit>
    suspend fun markPaid(transactionId: String): Result<Unit>
    suspend fun markPending(transactionId: String): Result<Unit>
    suspend fun delete(transactionId: String): Result<Unit>
    suspend fun getHistory(): Result<List<HistoryItem>>

    /** Income + expense for one month ("yyyy-MM"), merged and sorted newest-first. */
    suspend fun getMonth(monthKey: String): Result<List<HistoryItem>>
}
