package com.resolveprogramming.pocketcounter.domain.model

data class Series(
    val id: String,
    val name: String,
    val type: TransactionType,
    val recurrenceDay: Int?,
    val tagIds: List<String> = emptyList(),
)

data class CarryForwardResult(
    val createdCount: Int,
    val skippedCount: Int,
)
