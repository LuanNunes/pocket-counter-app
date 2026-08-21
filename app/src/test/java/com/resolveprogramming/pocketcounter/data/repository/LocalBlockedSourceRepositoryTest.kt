package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.BlockedSourceStore
import com.resolveprogramming.pocketcounter.domain.model.BlockedSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FakeBlockedSourceStore(initial: List<BlockedSource> = emptyList()) : BlockedSourceStore {
    private val _blocked = MutableStateFlow(initial)
    override val blocked: Flow<List<BlockedSource>> = _blocked
    override suspend fun update(transform: (List<BlockedSource>) -> List<BlockedSource>) {
        _blocked.value = transform(_blocked.value)
    }
}

class LocalBlockedSourceRepositoryTest {

    @Test
    fun `blocklist on an empty store is empty`() = runTest {
        val repo = LocalBlockedSourceRepository(FakeBlockedSourceStore())

        assertTrue(repo.blocklist.first().entries.isEmpty())
    }

    @Test
    fun `block persists a normalized entry`() = runTest {
        val store = FakeBlockedSourceStore()
        val repo = LocalBlockedSourceRepository(store)

        repo.block("Google", Instant.EPOCH)

        assertEquals("google", repo.blocklist.first().entries.single().key)
    }

    @Test
    fun `block is a no-op for an emoji-only label`() = runTest {
        val store = FakeBlockedSourceStore()
        val repo = LocalBlockedSourceRepository(store)

        repo.block("📉📈", Instant.EPOCH)

        assertTrue(repo.blocklist.first().entries.isEmpty())
    }

    @Test
    fun `block does not refresh blockedAt when re-blocking an already-blocked source`() = runTest {
        val store = FakeBlockedSourceStore()
        val repo = LocalBlockedSourceRepository(store)
        repo.block("Google", Instant.EPOCH)

        repo.block("Google", Instant.EPOCH.plusSeconds(100))

        assertEquals(Instant.EPOCH, repo.blocklist.first().entries.single().blockedAt)
    }

    @Test
    fun `unblock removes the entry by key`() = runTest {
        val store = FakeBlockedSourceStore()
        val repo = LocalBlockedSourceRepository(store)
        repo.block("Google", Instant.EPOCH)

        repo.unblock("google")

        assertTrue(repo.blocklist.first().entries.isEmpty())
    }
}
