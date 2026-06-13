package com.resolveprogramming.pocketcounter.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PaymentStatus {
    @SerialName("paid") PAID,
    @SerialName("pending") PENDING,
}
