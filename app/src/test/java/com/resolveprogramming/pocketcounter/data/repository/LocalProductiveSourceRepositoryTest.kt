package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.ProductiveSourceStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeProductiveSourceStore(initial: Map<String, Int> = emptyMap()) : ProductiveSourceStore {
    private val counts = initial.toMutableMap()
    override suspend fun counts(): Map<String, Int> = counts.toMap()
    override suspend fun increment(key: String) {
        counts[key] = (counts[key] ?: 0) + 1
    }
}

class LocalProductiveSourceRepositoryTest {

    @Test
    fun `countFor an unknown source is zero`() = runTest {
        val repo = LocalProductiveSourceRepository(FakeProductiveSourceStore())

        assertEquals(0, repo.countFor("Google"))
    }

    @Test
    fun `record then countFor returns one`() = runTest {
        val repo = LocalProductiveSourceRepository(FakeProductiveSourceStore())

        repo.record("Google")

        assertEquals(1, repo.countFor("Google"))
    }

    @Test
    fun `recording the same source repeatedly increments the count`() = runTest {
        val repo = LocalProductiveSourceRepository(FakeProductiveSourceStore())

        repeat(3) { repo.record("Nubank") }

        assertEquals(3, repo.countFor("Nubank"))
    }

    @Test
    fun `case and diacritics resolve to the same source`() = runTest {
        val repo = LocalProductiveSourceRepository(FakeProductiveSourceStore())

        repo.record("Google")
        repo.record("google")
        repo.record("Banco Itaú")

        assertEquals(2, repo.countFor("GOOGLE"))
        assertEquals(1, repo.countFor("banco itau"))
    }

    @Test
    fun `distinct sources are counted separately`() = runTest {
        val repo = LocalProductiveSourceRepository(FakeProductiveSourceStore())

        repo.record("Google")
        repo.record("Google Pay")

        assertEquals(1, repo.countFor("Google"))
        assertEquals(1, repo.countFor("Google Pay"))
    }

    @Test
    fun `record is a no-op for a label that normalizes to blank`() = runTest {
        val store = FakeProductiveSourceStore()
        val repo = LocalProductiveSourceRepository(store)

        repo.record("📉📈")

        assertTrue(store.counts().isEmpty())
        assertEquals(0, repo.countFor("📉📈"))
    }
}
