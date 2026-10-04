package com.resolveprogramming.pocketcounter.domain.model

/**
 * What `/classify` suggests for a notification: the one tag a matched SUGGEST rule points at.
 * Payment method, card and type are not part of the contract; they come from the parsed text.
 */
data class ClassificationSuggestion(
    val idTag: String? = null,
)
