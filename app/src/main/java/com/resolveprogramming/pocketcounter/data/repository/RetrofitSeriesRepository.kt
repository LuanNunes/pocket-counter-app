package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.cache.SuspendCache
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.SeriesApi
import com.resolveprogramming.pocketcounter.data.remote.dto.CarryForwardRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.CategorizeRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.CreateRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.data.remote.dto.RenameRecurringSeriesRequest
import com.resolveprogramming.pocketcounter.domain.model.CarryForwardResult
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitSeriesRepository @Inject constructor(
    private val api: SeriesApi,
) : SeriesRepository {

    private val seriesCache = SuspendCache<List<Series>>()

    override suspend fun getAll(): Result<List<Series>> = runCatching {
        seriesCache.get { api.getAll().map { it.toDomain() } }
    }

    override suspend fun create(name: String, type: TransactionType, recurrenceDay: Int?): Result<Series> =
        runCatching {
            api.create(CreateRecurringSeriesRequest(name, type.name, recurrenceDay)).toDomain()
        }.onSuccess { seriesCache.invalidate() }

    override suspend fun rename(id: String, name: String): Result<Unit> = runCatching {
        api.rename(id, RenameRecurringSeriesRequest(name))
        Unit
    }.onSuccess { seriesCache.invalidate() }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        api.delete(id)
    }.onSuccess { seriesCache.invalidate() }

    override suspend fun setTags(id: String, tagIds: List<String>): Result<Unit> = runCatching {
        api.setTags(id, CategorizeRecurringSeriesRequest(tagIds))
    }.onSuccess { seriesCache.invalidate() }

    override suspend fun linkTransaction(
        seriesId: String,
        transactionId: String,
        includePrevious: Boolean,
    ): Result<Unit> = runCatching {
        api.linkTransaction(seriesId, transactionId, includePrevious)
    }.onSuccess { seriesCache.invalidate() }

    override suspend fun unlinkTransaction(seriesId: String, transactionId: String): Result<Unit> =
        runCatching {
            api.unlinkTransaction(seriesId, transactionId)
        }.onSuccess { seriesCache.invalidate() }

    override suspend fun carryForward(
        targetRefYearMonth: Int,
        sourceRefYearMonth: Int,
        onlyRecurring: Boolean,
    ): Result<CarryForwardResult> = runCatching {
        api.carryForward(targetRefYearMonth, CarryForwardRequest(sourceRefYearMonth, onlyRecurring)).toDomain()
    }.onSuccess { seriesCache.invalidate() }
}
