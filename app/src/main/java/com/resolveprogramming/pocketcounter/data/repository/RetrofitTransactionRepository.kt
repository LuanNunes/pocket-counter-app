package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toHistoryItem
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ReorderItemDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionReorderRequest
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitTransactionRepository @Inject constructor(
    private val api: TransactionApi,
) : TransactionRepository {

    override suspend fun getHistory(): Result<List<HistoryItem>> =
        getMonth(monthKeyFromRef(RemoteMappers.currentRefYearMonth()))

    override suspend fun getMonth(monthKey: String): Result<List<HistoryItem>> = runCatching {
        val ref = RemoteMappers.monthKeyToRef(monthKey)
        val incomes = api.getIncomes(ref)
        val expenses = api.getExpenses(ref)
        // Honor the backend manual order (displayOrder) so reorders persist; fall back to
        // newest-first when nothing has been reordered (all displayOrder == 0).
        (incomes + expenses)
            .map { it.toHistoryItem() }
            .sortedWith(HistoryItem.LEDGER_ORDER)
    }

    override suspend fun save(draft: WizardDraft): Result<String> = runCatching {
        val type = draft.type ?: error("Type is required")
        val dto = draft.toDto()
        run {
            if (type == TransactionType.INCOME) return@run api.addIncome(dto)
            api.addExpense(dto)
        }
    }

    override suspend fun update(transactionId: String, draft: WizardDraft): Result<String> = runCatching {
        api.update(transactionId, draft.toDto())
    }

    override suspend fun setTags(item: HistoryItem, tagIds: List<String>?): Result<Unit> = runCatching {
        // Fetch the existing DTO and copy only its tags, so description/currency/FX/displayOrder
        // survive a tag-only edit (the backend update copies those verbatim from the body).
        val ref = RemoteMappers.refYearMonth(item.date)
        val existing = run {
            if (item.type == TransactionType.INCOME) return@run api.getIncomes(ref)
            api.getExpenses(ref)
        }
        val dto = existing.firstOrNull { it.id == item.id } ?: error("Transaction ${item.id} not found")
        // null = inherit (clears override); non-null = override with these tags.
        val updated = dto.copy(tags = tagIds?.map { TagDto(id = it, name = "") })
        api.update(item.id, updated)
    }

    override suspend fun reorder(orderedIds: List<String>): Result<Unit> = runCatching {
        val items = orderedIds.mapIndexed { index, id -> ReorderItemDto(id = id, displayOrder = index) }
        api.reorder(TransactionReorderRequest(items))
    }

    override suspend fun markPaid(transactionId: String): Result<Unit> = runCatching {
        api.markPaid(transactionId)
    }

    override suspend fun markPending(transactionId: String): Result<Unit> = runCatching {
        api.markPending(transactionId)
    }

    override suspend fun delete(transactionId: String): Result<Unit> = runCatching {
        api.delete(transactionId)
    }

    private fun WizardDraft.toDto(): TransactionDto {
        val type = type ?: error("Type is required")
        val amount = amount?.abs() ?: error("Amount is required")
        val date = date ?: error("Date is required")
        val isPaid = statusPayment == PaymentStatus.PAID
        // Recurrence (isFixo) is not sent as a transaction field; it persists via series
        // linkage (idSeries). The WizardViewModel creates/links the recurring series after a
        // successful save. The new backend has no isFixo column.
        return TransactionDto(
            transactionType = "INCOME".takeIf { type == TransactionType.INCOME } ?: "EXPENSE",
            amount = amount,
            statusPayment = "PAID".takeIf { isPaid } ?: "PENDING",
            refYearMonth = RemoteMappers.refYearMonth(date),
            dateDue = date.toString(),
            datePaid = date.toString().takeIf { isPaid },
            tags = tagIds.map { TagDto(id = it, name = "") },
            // Preserve manual sort position across edits (0 for new rows).
            displayOrder = displayOrder,
            paymentMethod = paymentMethod?.name,
            cardId = cardId,
            idSeries = seriesId,
        )
    }

    private fun monthKeyFromRef(ref: Int): String = "%04d-%02d".format(ref / 100, ref % 100)
}
