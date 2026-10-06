package com.resolveprogramming.pocketcounter.data.local

import com.resolveprogramming.pocketcounter.domain.model.BlockedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/** Robolectric only for a real org.json; the store's DataStore file is never touched here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreBlockedSourceStoreTest {

    private val store = DataStoreBlockedSourceStore(RuntimeEnvironment.getApplication())

    @Test
    fun `encode then parse round-trips the entries including the blockedAt instant`() {
        val sources = listOf(
            BlockedSource(label = "Google", blockedAt = Instant.parse("2026-06-12T10:15:30Z")),
            BlockedSource(label = "Nubank", blockedAt = Instant.EPOCH),
        )

        assertEquals(sources, store.parse(store.encode(sources)))
    }

    @Test
    fun `parse of null is empty`() {
        assertTrue(store.parse(null).isEmpty())
    }

    @Test
    fun `parse of a corrupt blob is empty instead of throwing`() {
        assertTrue(store.parse("{not json at all").isEmpty())
    }

    @Test
    fun `parse of an entry with an unparseable blockedAt is empty instead of throwing`() {
        val raw = """[{"label":"Google","blockedAt":"ontem"}]"""

        assertTrue(store.parse(raw).isEmpty())
    }
}
