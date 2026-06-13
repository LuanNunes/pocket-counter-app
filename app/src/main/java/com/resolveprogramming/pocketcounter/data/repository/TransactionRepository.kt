package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft

interface TransactionRepository {
    suspend fun save(draft: WizardDraft): Result<String>
    suspend fun update(transactionId: String, draft: WizardDraft): Result<String>

    /** Sets a transaction's own tags. null = inherit the source defaults; non-null = override. */
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
