package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.local.IssuerCardStore
import com.resolveprogramming.pocketcounter.domain.notification.IssuerCardMatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeIssuerCardStore(initial: Map<String, String> = emptyMap()) : IssuerCardStore {
    private var map = initial.toMap()

    override suspend fun getMap(): Map<String, String> = map

    override suspend fun update(transform: (Map<String, String>) -> Map<String, String>) {
        map = transform(map)
    }
}

/**
 * Records what the Home picker taught, so the confirm path can be asserted end to end. Normalizes
 * on the way in like [LocalIssuerCardRepository], so what a test writes matches what
 * `IssuerCardMatcher.resolve` looks up.
 */
class FakeIssuerCardRepository(initial: Map<String, String> = emptyMap()) : IssuerCardRepository {
    private val map = initial.toMutableMap()

    override suspend fun getMap(): Map<String, String> = map.toMap()

    override suspend fun associate(issuerKey: String, cardId: String) {
        map[IssuerCardMatcher.normalizeIssuer(issuerKey)] = cardId
    }

    override suspend fun clear(issuerKey: String) {
        map.remove(IssuerCardMatcher.normalizeIssuer(issuerKey))
    }
}

class LocalIssuerCardRepositoryTest {

    @Test
    fun `getMap on an empty store returns an empty map`() = runTest {
        val repo = LocalIssuerCardRepository(FakeIssuerCardStore())

        assertTrue(repo.getMap().isEmpty())
    }

    @Test
    fun `associate stores under the normalized issuer key`() = runTest {
        val store = FakeIssuerCardStore()
        val repo = LocalIssuerCardRepository(store)

        repo.associate("Nubank ", "card-1")

        val snapshot = store.getMap()
        assertTrue("normalized key 'nubank' must be stored", snapshot.containsKey("nubank"))
        assertFalse("raw key must NOT be stored", snapshot.containsKey("Nubank "))
    }

    @Test
    fun `associate round-trips through getMap`() = runTest {
        val repo = LocalIssuerCardRepository(FakeIssuerCardStore())

        repo.associate("Itaú", "card-9")

        assertEquals(mapOf("itau" to "card-9"), repo.getMap())
    }

    @Test
    fun `two cards from one issuer collapse to one key and the last taught wins`() = runTest {
        val repo = LocalIssuerCardRepository(FakeIssuerCardStore())

        repo.associate("Nubank", "card-1")
        repo.associate("nubank", "card-2")

        assertEquals(mapOf("nubank" to "card-2"), repo.getMap())
    }

    @Test
    fun `clear normalizes the key before removing`() = runTest {
        val repo = LocalIssuerCardRepository(FakeIssuerCardStore())
        repo.associate("nubank", "card-1")

        repo.clear("Nubank!")

        assertTrue(repo.getMap().isEmpty())
    }
}
