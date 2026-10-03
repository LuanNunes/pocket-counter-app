package com.resolveprogramming.pocketcounter.domain.model

data class ClassificationSuggestion(
    val tagIds: List<String>,
    val paymentMethod: PaymentMethod? = null,
    val cardId: String? = null,
    /**
     * Transaction type carried by a matched SUGGEST rule. Used as a fallback when the per-notification
     * parse couldn't determine the type ([WizardDraft.fromNotification]); the `/pending` list carries
     * no suggestions, so this is only ever populated from a `/classify` response.
     */
    val transactionType: TransactionType? = null,
)
