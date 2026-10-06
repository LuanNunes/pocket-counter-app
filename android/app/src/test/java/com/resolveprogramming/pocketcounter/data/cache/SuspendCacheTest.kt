package com.resolveprogramming.pocketcounter.data.cache

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SuspendCacheTest {

    @Test
    fun `caches the first value so a second read does not reload`() = runTest {
        val cache = SuspendCache<Int>()
        var loads = 0

        cache.get { ++loads }
        val second = cache.get { ++loads }

        assertEquals(1, second)
        assertEquals(1, loads)
    }

    @Test
    fun `does not cache a failed load so the next read retries`() = runTest {
        val cache = SuspendCache<Int>()
        var loads = 0

        runCatching { cache.get { loads++; error("boom") } }
        val value = cache.get { ++loads }

        assertEquals(2, value)
        assertEquals(2, loads)
    }

    @Test
    fun `reloads once the entry has aged past the ttl`() = runTest {
        var clock = 0L
        val cache = SuspendCache<Int>(ttlMillis = 100L, now = { clock })
        var loads = 0

        cache.get { ++loads } // stored at t=0
        clock = 99L
        cache.get { ++loads } // still fresh
        clock = 100L
        val afterExpiry = cache.get { ++loads } // aged out → reloads

        assertEquals(2, afterExpiry)
        assertEquals(2, loads)
    }

    @Test
    fun `zero ttl means the value never expires`() = runTest {
        var clock = 0L
        val cache = SuspendCache<Int>(ttlMillis = SuspendCache.NO_EXPIRY, now = { clock })
        var loads = 0

        cache.get { ++loads }
        clock = Long.MAX_VALUE
        val later = cache.get { ++loads }

        assertEquals(1, later)
        assertEquals(1, loads)
    }

    @Test
    fun `invalidate forces the next read to reload`() = runTest {
        val cache = SuspendCache<Int>()
        var loads = 0

        cache.get { ++loads }
        cache.invalidate()
        val reloaded = cache.get { ++loads }

        assertEquals(2, reloaded)
        assertEquals(2, loads)
    }
}
