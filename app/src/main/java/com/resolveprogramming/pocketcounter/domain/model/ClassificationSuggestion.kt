package com.resolveprogramming.pocketcounter.domain.model

data class ClassificationSuggestion(
    val tagIds: List<String>,
    val paymentMethod: PaymentMethod? = null,
    val cardId: String? = null,
)
