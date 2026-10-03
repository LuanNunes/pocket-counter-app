package com.resolveprogramming.pocketcounter.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-value suspend cache for repository lookup reads. Caches only a successfully-loaded
 * value; if [load] throws, the exception propagates and nothing is stored, so a transient
 * failure is never sticky. Concurrent first-callers coalesce on the mutex.
 *
 * When [ttlMillis] is positive the stored value self-expires that many milliseconds after it was
 * loaded, so a value changed elsewhere (e.g. on the web) is eventually picked up without an explicit
 * [invalidate]. [ttlMillis] == [NO_EXPIRY] keeps the value until [invalidate] is called. Timing uses
 * a monotonic clock ([now]) so a wall-clock change can't extend or expire an entry; [now] is
 * injectable for tests.
 */
class SuspendCache<T>(
    private val ttlMillis: Long = NO_EXPIRY,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 },
) {

    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    @Volatile
    private var storedAt: Long = 0

    suspend fun get(load: suspend () -> T): T {
        fresh()?.let { return it }
        return mutex.withLock {
            fresh() ?: load().also {
                value = it
                storedAt = now()
            }
        }
    }

    fun invalidate() {
        value = null
    }

    /** The stored value, or null when nothing is cached or the entry has aged past [ttlMillis]. */
    private fun fresh(): T? {
        val current = value ?: return null
        if (ttlMillis != NO_EXPIRY && now() - storedAt >= ttlMillis) return null
        return current
    }

    companion object {
        const val NO_EXPIRY = 0L
    }
}
