package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem

interface NotificationRepository {
    suspend fun ingest(captured: CapturedMessage): Result<NotificationItem>
    suspend fun getAll(): Result<List<NotificationItem>>
    suspend fun getById(id: String): Result<NotificationItem>
    suspend fun getPendingReview(): Result<List<NotificationItem>>
    suspend fun markIgnored(id: String): Result<Unit>
    suspend fun classify(notificationId: String, base: NotificationItem): Result<ClassifiedNotification>
    suspend fun markClassified(notificationId: String, transactionId: String): Result<Unit>
}
