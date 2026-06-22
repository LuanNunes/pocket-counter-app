package com.resolveprogramming.pocketcounter.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PaymentMethod {
    CREDIT,
    DEBIT,
    PIX,
    CASH,
    CRYPTO,
}
