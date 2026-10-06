package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist

/**
 * In-memory [ProductiveSourceRepository] for unit tests. Keys through [SourceBlocklist.keyOf],
 * exactly as the real impl does, so tests can assert on normalized keys.
 */
class FakeProductiveSourceRepository(
    initial: Map<String, Int> = emptyMap(),
) : ProductiveSourceRepository {
    private val counts = initial.toMutableMap()

    override suspend fun countFor(app: String): Int {
        val key = SourceBlocklist.keyOf(app) ?: return 0
        return counts[key] ?: 0
    }

    override suspend fun record(app: String) {
        val key = SourceBlocklist.keyOf(app) ?: return
        counts[key] = (counts[key] ?: 0) + 1
    }
}
