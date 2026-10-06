package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.BlockedSource
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import java.time.Instant

/**
 * The set of notification sources (app labels) the user has chosen to stop capturing entirely.
 * Matching is exact normalized-key equality — never a prefix/contains check — so blocking
 * "Google" can't also silence "Google Pay".
 */
data class SourceBlocklist(val entries: List<BlockedSource>) {

    /** SMS is always user-forwarded (the user chose to forward it), so it is never blockable. */
    fun blocks(app: String, channel: NotificationChannel): Boolean {
        if (channel == NotificationChannel.SMS) return false
        val key = keyOf(app) ?: return false
        return entries.any { it.key == key }
    }

    /** Idempotent: re-blocking an already-blocked source keeps its original [BlockedSource.blockedAt]. */
    fun with(app: String, at: Instant): SourceBlocklist {
        val key = keyOf(app) ?: return this
        if (entries.any { it.key == key }) return this
        return copy(entries = entries + BlockedSource(label = app, blockedAt = at))
    }

    fun without(key: String): SourceBlocklist = copy(entries = entries.filterNot { it.key == key })

    val recentFirst: List<BlockedSource>
        get() = entries.sortedByDescending { it.blockedAt }

    companion object {
        val EMPTY = SourceBlocklist(entries = emptyList())

        /** Null when [app] normalizes to blank (emoji-only/symbol-only) — never a valid wildcard key. */
        fun keyOf(app: String): String? = SourceKey.normalize(app).takeIf { it.isNotBlank() }

        /** Whether [app] is blockable at all. Independent of what is already blocked. */
        fun canBlock(app: String, channel: NotificationChannel): Boolean =
            channel != NotificationChannel.SMS && keyOf(app) != null
    }
}
