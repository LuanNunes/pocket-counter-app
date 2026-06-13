package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.PaymentSource

/** Payload for creating/editing a Meio de pagamento (Gestão). [type] is the backend enum name. */
data class PaymentSourceInput(
    val name: String,
    val type: String,
    val allowsIncome: Boolean,
    val allowsExpense: Boolean,
    val refDayBill: Int? = null,
)

interface PaymentSourceRepository {
    suspend fun getAll(): Result<List<PaymentSource>>
    suspend fun getById(id: String): Result<PaymentSource>
    suspend fun create(input: PaymentSourceInput): Result<PaymentSource>
    suspend fun update(id: String, input: PaymentSourceInput): Result<PaymentSource>
    suspend fun delete(id: String): Result<Unit>
}
