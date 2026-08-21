package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BlockedSourceRepository {
    val blocklist: Flow<SourceBlocklist>
    suspend fun block(app: String, at: Instant)
    suspend fun unblock(key: String)
}
