package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import java.math.BigDecimal

/** Full payload for creating/editing a Fonte (Gestão). */
data class SourceInput(
    val name: String,
    val idPaymentSource: String,
    val allowsIncome: Boolean,
    val allowsExpense: Boolean,
    val refDayRecurring: Int? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    val tagIds: List<String> = emptyList(),
)

interface SourceRepository {
    suspend fun getAll(): Result<List<Source>>
    suspend fun getByPaymentSourceAndType(
        idPaymentSource: String,
        type: TransactionType,
    ): Result<List<Source>>

    suspend fun create(name: String, idPaymentSource: String, type: TransactionType): Result<Source>
    suspend fun create(input: SourceInput): Result<Source>
    suspend fun update(id: String, input: SourceInput): Result<Source>
    suspend fun delete(id: String): Result<Unit>
}
