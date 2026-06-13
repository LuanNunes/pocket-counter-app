package com.resolveprogramming.pocketcounter.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class TransactionType {
    @SerialName("income") INCOME,
    @SerialName("expense") EXPENSE,
}
