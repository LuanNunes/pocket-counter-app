package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.api.PaymentSourceApi
import com.resolveprogramming.pocketcounter.data.remote.dto.PaymentSourceDto
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitPaymentSourceRepository @Inject constructor(
    private val api: PaymentSourceApi,
) : PaymentSourceRepository {

    override suspend fun getAll(): Result<List<PaymentSource>> = runCatching {
        api.getPaymentSources().map { it.toDomain() }
    }

    override suspend fun getById(id: String): Result<PaymentSource> = runCatching {
        api.getPaymentSources().first { (it.id ?: it.name) == id }.toDomain()
    }

    override suspend fun create(input: PaymentSourceInput): Result<PaymentSource> = runCatching {
        // The create endpoint returns the new id (quoted); re-read for the canonical row,
        // but never fail a successful write if the row isn't visible yet — fall back to local.
        val id = api.create(input.toDto()).trim('"')
        api.getPaymentSources().firstOrNull { (it.id ?: it.name) == id }?.toDomain()
            ?: input.toDto(id).toDomain()
    }

    override suspend fun update(id: String, input: PaymentSourceInput): Result<PaymentSource> = runCatching {
        api.update(id, input.toDto(id))
        api.getPaymentSources().firstOrNull { (it.id ?: it.name) == id }?.toDomain()
            ?: input.toDto(id).toDomain()
    }

    override suspend fun delete(id: String): Result<Unit> = runCatching {
        api.delete(id)
    }

    private fun PaymentSourceInput.toDto(id: String? = null) = PaymentSourceDto(
        id = id,
        name = name,
        type = type,
        allowsIncome = allowsIncome,
        allowsExpense = allowsExpense,
        refDayBill = refDayBill,
    )
}
