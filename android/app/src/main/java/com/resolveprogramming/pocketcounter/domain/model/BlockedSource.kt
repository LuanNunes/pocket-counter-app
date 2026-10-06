package com.resolveprogramming.pocketcounter.domain.model

import com.resolveprogramming.pocketcounter.domain.notification.SourceKey
import java.time.Instant

/** A notification source (an app label) the user chose to stop capturing entirely. */
data class BlockedSource(val label: String, val blockedAt: Instant) {

    /** Derived, never persisted: a stored key would go stale if [SourceKey.normalize] ever changed. */
    val key: String get() = SourceKey.normalize(label)
}
