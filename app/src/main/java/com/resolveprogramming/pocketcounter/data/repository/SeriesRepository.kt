package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.CarryForwardResult
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.TransactionType

interface SeriesRepository {
    suspend fun getAll(): Result<List<Series>>
    suspend fun create(name: String, type: TransactionType, recurrenceDay: Int?): Result<Series>
    suspend fun rename(id: String, name: String): Result<Unit>
    suspend fun delete(id: String): Result<Unit>
    suspend fun setTags(id: String, tagIds: List<String>): Result<Unit>
    suspend fun linkTransaction(seriesId: String, transactionId: String, includePrevious: Boolean): Result<Unit>
    suspend fun unlinkTransaction(seriesId: String, transactionId: String): Result<Unit>
    suspend fun carryForward(
        targetRefYearMonth: Int,
        sourceRefYearMonth: Int,
        onlyRecurring: Boolean,
    ): Result<CarryForwardResult>
}
