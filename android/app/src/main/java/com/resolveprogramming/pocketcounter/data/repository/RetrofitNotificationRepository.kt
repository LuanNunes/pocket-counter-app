package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toClassified
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toDomain
import com.resolveprogramming.pocketcounter.data.remote.RemoteMappers.toRequestDto
import com.resolveprogramming.pocketcounter.data.remote.api.NotificationApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifiedRequestDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassifyRequestDto
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.notification.BrNotificationParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RetrofitNotificationRepository @Inject constructor(
    private val api: NotificationApi,
) : NotificationRepository {

    override suspend fun ingest(captured: CapturedMessage): Result<NotificationItem> = runCatching {
        // Intentional re-parse: the ingestor's gate reads only the date-independent `amount`; the
        // date shipped to the backend must come from this single parse, not a stale `now`.
        val result = BrNotificationParser.parse(captured.text)
        require(result.isFinancial) { "Non-financial message dropped before POST" }
        api.add(captured.toRequestDto(result.parsed)).toDomain()
    }

    override suspend fun getAll(): Result<List<NotificationItem>> = runCatching {
        api.getNotifications().map { it.toDomain() }
    }

    override suspend fun getById(id: String): Result<NotificationItem> = runCatching {
        api.getNotifications().first { it.id == id }.toDomain()
    }

    override suspend fun getPendingReview(): Result<List<NotificationItem>> = runCatching {
        api.getPending().map { it.toDomain() }
    }

    override suspend fun markIgnored(id: String): Result<Unit> = runCatching {
        api.ignore(id)
    }

    override suspend fun classify(
        notificationId: String,
        base: NotificationItem,
    ): Result<ClassifiedNotification> = runCatching {
        api.classify(ClassifyRequestDto(notificationId)).toClassified(base)
    }

    override suspend fun markClassified(
        notificationId: String,
        transactionId: String,
    ): Result<Unit> = runCatching {
        api.markClassified(notificationId, ClassifiedRequestDto(transactionId))
    }

}
