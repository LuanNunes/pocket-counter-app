package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.BlockedSourceStore
import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [BlockedSourceRepository]. Normalization and idempotency both live in
 * [SourceBlocklist] itself, so what is written matches what [SourceBlocklist.blocks] looks up.
 */
@Singleton
class LocalBlockedSourceRepository @Inject constructor(
    private val store: BlockedSourceStore,
) : BlockedSourceRepository {

    override val blocklist: Flow<SourceBlocklist> = store.blocked.map { SourceBlocklist(it) }

    override suspend fun block(app: String, at: Instant) {
        store.update { entries -> SourceBlocklist(entries).with(app, at).entries }
    }

    override suspend fun unblock(key: String) {
        store.update { entries -> SourceBlocklist(entries).without(key).entries }
    }
}
