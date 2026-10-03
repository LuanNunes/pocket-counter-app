package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/**
 * In-memory [BlockedSourceRepository] for unit tests. Normalization and idempotency come from
 * [SourceBlocklist] itself, exactly as in the real impl.
 */
class FakeBlockedSourceRepository(
    initial: SourceBlocklist = SourceBlocklist.EMPTY,
) : BlockedSourceRepository {
    private val flow = MutableStateFlow(initial)
    override val blocklist: Flow<SourceBlocklist> = flow

    override suspend fun block(app: String, at: Instant) {
        flow.value = flow.value.with(app, at)
    }

    override suspend fun unblock(key: String) {
        flow.value = flow.value.without(key)
    }
}
