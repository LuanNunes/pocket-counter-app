package com.resolveprogramming.pocketcounter.domain.model

import java.time.Instant

/** A raw SMS or push message captured on-device, before parsing and ingestion. */
data class CapturedMessage(
    val app: String,
    val channel: NotificationChannel,
    val text: String,
    val receivedAt: Instant,
)
