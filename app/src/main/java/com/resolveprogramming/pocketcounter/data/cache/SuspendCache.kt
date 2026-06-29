package com.resolveprogramming.pocketcounter.data.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-value suspend cache for repository lookup reads. Caches only a successfully-loaded
 * value; if [load] throws, the exception propagates and nothing is stored, so a transient
 * failure is never sticky. Concurrent first-callers coalesce on the mutex.
 */
class SuspendCache<T> {

    private val mutex = Mutex()

    @Volatile
    private var value: T? = null

    suspend fun get(load: suspend () -> T): T {
        value?.let { return it }
        return mutex.withLock {
            value ?: load().also { value = it }
        }
    }

    fun invalidate() {
        value = null
    }
}
