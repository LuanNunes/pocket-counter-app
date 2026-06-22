package com.resolveprogramming.pocketcounter.domain.model

data class ClassificationSuggestion(
    val idPaymentSource: String?,
    val idSource: String?,
    val tagIds: List<String>,
    val paymentMethod: PaymentMethod? = null,
    val cardId: String? = null,
)
