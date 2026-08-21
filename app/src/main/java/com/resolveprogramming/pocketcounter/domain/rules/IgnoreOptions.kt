package com.resolveprogramming.pocketcounter.domain.rules

import com.resolveprogramming.pocketcounter.domain.model.IgnoreScope
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist

/**
 * Decides the single learn scope a "Ignorar" dialog may offer — never [IgnoreScope.ThisOnly], and
 * never source-blocking alongside a derivable pattern: an app that sends derivable merchants must
 * not be one tap away from being silenced entirely.
 */
object IgnoreOptions {
    fun resolve(pattern: String?, app: String, channel: NotificationChannel): IgnoreScope? {
        if (!pattern.isNullOrBlank()) return IgnoreScope.Pattern(pattern)
        if (SourceBlocklist.canBlock(app, channel)) return IgnoreScope.Source(app)
        return null
    }
}
