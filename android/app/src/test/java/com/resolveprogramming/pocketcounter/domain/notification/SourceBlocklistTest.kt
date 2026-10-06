package com.resolveprogramming.pocketcounter.domain.notification

import com.resolveprogramming.pocketcounter.domain.model.BlockedSource
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SourceBlocklistTest {

    @Test
    fun `EMPTY blocks nothing`() {
        assertFalse(SourceBlocklist.EMPTY.blocks("Google", NotificationChannel.PUSH))
    }

    @Test
    fun `keyOf normalizes the app label`() {
        assertEquals("google", SourceBlocklist.keyOf("Google"))
    }

    @Test
    fun `keyOf is null for an emoji-only label`() {
        assertNull(SourceBlocklist.keyOf("📉📈"))
    }

    @Test
    fun `blocks true for an app matching a blocked entry key`() {
        val blocklist = SourceBlocklist(
            entries = listOf(BlockedSource(label = "Google", blockedAt = Instant.EPOCH)),
        )

        assertTrue(blocklist.blocks("Google", NotificationChannel.PUSH))
    }

    @Test
    fun `blocks false for an app with no matching entry`() {
        val blocklist = SourceBlocklist(
            entries = listOf(BlockedSource(label = "Google", blockedAt = Instant.EPOCH)),
        )

        assertFalse(blocklist.blocks("Banco Itaú", NotificationChannel.PUSH))
    }

    @Test
    fun `blocks is always false for SMS even when the source is blocked`() {
        val blocklist = SourceBlocklist(
            entries = listOf(BlockedSource(label = "Google", blockedAt = Instant.EPOCH)),
        )

        assertFalse(blocklist.blocks("Google", NotificationChannel.SMS))
    }

    @Test
    fun `canBlock is always false for SMS`() {
        assertFalse(SourceBlocklist.canBlock("Google", NotificationChannel.SMS))
    }

    @Test
    fun `canBlock is true for a PUSH app with a normalizable label`() {
        assertTrue(SourceBlocklist.canBlock("Google", NotificationChannel.PUSH))
    }

    @Test
    fun `canBlock is false for a PUSH app whose label normalizes to blank`() {
        assertFalse(SourceBlocklist.canBlock("📉", NotificationChannel.PUSH))
    }

    @Test
    fun `with is a no-op for a label that normalizes to blank`() {
        val blocklist = SourceBlocklist.EMPTY.with("📉", Instant.EPOCH)

        assertTrue(blocklist.entries.isEmpty())
    }

    @Test
    fun `blocking Google does not block Google Pay`() {
        val blocklist = SourceBlocklist.EMPTY.with("Google", Instant.EPOCH)

        assertFalse(blocklist.blocks("Google Pay", NotificationChannel.PUSH))
    }

    @Test
    fun `with re-blocking an already-blocked source does not refresh blockedAt`() {
        val blocklist = SourceBlocklist.EMPTY.with("Google", Instant.EPOCH)

        val reBlocked = blocklist.with("Google", Instant.EPOCH.plusSeconds(100))

        assertEquals(Instant.EPOCH, reBlocked.entries.single().blockedAt)
    }

    @Test
    fun `without removes the entry matching key`() {
        val blocklist = SourceBlocklist.EMPTY.with("Google", Instant.EPOCH)

        val unblocked = blocklist.without("google")

        assertTrue(unblocked.entries.isEmpty())
    }

    @Test
    fun `recentFirst orders most-recently-blocked first`() {
        val blocklist = SourceBlocklist.EMPTY
            .with("Google", Instant.EPOCH)
            .with("Nubank", Instant.EPOCH.plusSeconds(100))

        assertEquals(listOf("nubank", "google"), blocklist.recentFirst.map { it.key })
    }
}
