package com.resolveprogramming.pocketcounter.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class NotificationChannel {
    @SerialName("SMS") SMS,
    @SerialName("Push") PUSH,
}
