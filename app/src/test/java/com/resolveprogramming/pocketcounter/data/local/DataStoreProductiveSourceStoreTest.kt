package com.resolveprogramming.pocketcounter.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Robolectric only for a real org.json; the store's DataStore file is never touched here. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreProductiveSourceStoreTest {

    private val store = DataStoreProductiveSourceStore(RuntimeEnvironment.getApplication())

    @Test
    fun `encode then parse round-trips every counter`() {
        val counts = mapOf("google" to 1, "nubank" to 42)

        assertEquals(counts, store.parse(store.encode(counts)))
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
    fun `parse of a non-numeric count is empty instead of throwing`() {
        assertTrue(store.parse("""{"google":"muitas"}""").isEmpty())
    }
}
