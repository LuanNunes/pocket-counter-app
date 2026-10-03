package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.ProductiveSourceStore
import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [ProductiveSourceRepository]. Keys through [SourceBlocklist.keyOf], so a count is
 * looked up under exactly the key the blocklist would block — and a label too degenerate to block is
 * too degenerate to count.
 */
@Singleton
class LocalProductiveSourceRepository @Inject constructor(
    private val store: ProductiveSourceStore,
) : ProductiveSourceRepository {

    override suspend fun countFor(app: String): Int {
        val key = SourceBlocklist.keyOf(app) ?: return 0
        return store.counts()[key] ?: 0
    }

    override suspend fun record(app: String) {
        val key = SourceBlocklist.keyOf(app) ?: return
        store.increment(key)
    }
}
