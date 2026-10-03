package com.resolveprogramming.pocketcounter.domain.model

data class NotificationItem(
    val id: String,
    val app: String,
    val channel: NotificationChannel,
    val time: String,
    val received: String,
    val text: String,
    val status: NotificationStatus,
    val parsed: ParsedNotification,
    val suggestions: ClassificationSuggestion,
    val tokens: List<Token>,
)
