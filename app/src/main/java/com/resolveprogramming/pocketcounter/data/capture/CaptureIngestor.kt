package com.resolveprogramming.pocketcounter.data.capture

import android.util.Log
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.di.ApplicationScope
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.notification.BrNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point from the platform capture services (notification listener / SMS receiver).
 * Off-loads parse, relevance filtering and a best-effort POST to the application scope so the
 * main thread / receiver are never blocked.
 *
 * The parse + dedup decision is factored into [shouldIngest] so it stays unit-testable without
 * Android. Dedup is an in-memory, single-process bounded LRU — cross-process / persistent dedup
 * is out of scope.
 */
@Singleton
class CaptureIngestor @Inject constructor(
    private val repository: NotificationRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Content key → last-seen epoch second. Access-ordered, bounded LRU. */
    private val seen = object : LinkedHashMap<Int, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Long>?): Boolean =
            size > MAX_ENTRIES
    }

    fun submit(captured: CapturedMessage) {
        if (!shouldIngest(captured)) return
        scope.launch {
            // Intentional re-parse downstream: this gate reads only the date-independent `amount`,
            // while RetrofitNotificationRepository.ingest re-parses to ship the message's own date.
            repository.ingest(captured)
                .onFailure { Log.w(TAG, "ingest failed", it) }
        }
    }

    /** Returns true when [captured] is financial and not a recent duplicate (and records it). */
    fun shouldIngest(captured: CapturedMessage): Boolean {
        if (!BrNotificationParser.parse(captured.text).isFinancial) return false
        val key = dedupKey(captured)
        val now = captured.receivedAt.epochSecond
        synchronized(seen) {
            val lastSeen = seen[key]
            if (lastSeen != null && now - lastSeen <= DEDUP_WINDOW_SECONDS) return false
            seen[key] = now
        }
        return true
    }

    private fun dedupKey(captured: CapturedMessage): Int {
        val normalized = captured.text.trim().replace(WHITESPACE, " ").lowercase()
        var result = captured.channel.hashCode()
        result = 31 * result + normalized.hashCode()
        return result
    }

    private companion object {
        const val TAG = "CaptureIngestor"
        const val MAX_ENTRIES = 200
        const val DEDUP_WINDOW_SECONDS = 10L
        val WHITESPACE = Regex("\\s+")
    }
}
