package com.resolveprogramming.pocketcounter.domain.model

data class ClassifiedNotification(
    val notification: NotificationItem,
    val pendingTransactionId: String?,
)
