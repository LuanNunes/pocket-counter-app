package com.resolveprogramming.pocketcounter.platform.capture

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.resolveprogramming.pocketcounter.data.capture.CaptureIngestor
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import javax.inject.Inject

/**
 * Captures posted push notifications and hands them to [CaptureIngestor]. Stays a thin shell:
 * no parsing or networking here (this runs on the main thread). Requires the user to grant
 * Notification Access in system settings.
 */
@AndroidEntryPoint
class PocketNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var ingestor: CaptureIngestor

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()

        val text = listOf(title, body).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (text.isBlank()) return

        ingestor.submit(
            CapturedMessage(
                app = appLabel(sbn.packageName),
                channel = NotificationChannel.PUSH,
                text = text,
                receivedAt = Instant.ofEpochMilli(sbn.postTime),
            ),
        )
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
    }.getOrDefault(pkg)
}
