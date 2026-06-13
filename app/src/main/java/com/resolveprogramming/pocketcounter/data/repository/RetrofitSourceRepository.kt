package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.SourceApi
import com.resolveprogramming.pocketcounter.data.remote.dto.SourceDto
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitSourceRepository @Inject constructor(
    private val api: SourceApi,
) : SourceRepository {

    override suspend fun getAll(): Result<List<Source>> = runCatching {
        api.getSources().map { it.toDomain() }
    }

    override suspend fun getByPaymentSourceAndType(
        idPaymentSource: String,
        type: TransactionType,
    ): Result<List<Source>> = runCatching {
        api.getSources().map { it.toDomain() }.filter {
            it.idPaymentSource == idPaymentSource &&
                if (type == TransactionType.INCOME) it.allowsIncome else it.allowsExpense
        }
    }

    override suspend fun create(
        name: String,
        idPaymentSource: String,
        type: TransactionType,
    ): Result<Source> = create(
        SourceInput(
            name = name,
            idPaymentSource = idPaymentSource,
            allowsIncome = type == TransactionType.INCOME,
            allowsExpense = type == TransactionType.EXPENSE,
        ),
    )

    override suspend fun create(input: SourceInput): Result<Source> = runCatching {
        val id = api.addSource(input.toDto()).trim('"')
        input.toDomain(id)
    }

    override suspend fun update(id: String, input: SourceInput): Result<Source> = runCatching {
        api.update(id, input.toDto(id))
        // Returns the input echo (not a server re-read); callers refetch the list after editing.
        input.toDomain(id)
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        api.delete(id)
    }

    private fun SourceInput.toDto(id: String? = null) = SourceDto(
        id = id,
        idPaymentSource = idPaymentSource,
        name = name,
        allowsIncome = allowsIncome,
        allowsExpense = allowsExpense,
        refDayRecurring = refDayRecurring,
        amount = amount,
        tags = tagIds,
    )

    private fun SourceInput.toDomain(id: String) = Source(
        id = id,
        name = name,
        idPaymentSource = idPaymentSource,
        allowsExpense = allowsExpense,
        allowsIncome = allowsIncome,
        refDayRecurring = refDayRecurring,
        amount = amount,
        tags = tagIds,
    )
}
