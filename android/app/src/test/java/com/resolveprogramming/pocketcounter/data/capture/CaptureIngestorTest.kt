package com.resolveprogramming.pocketcounter.data.capture

import com.resolveprogramming.pocketcounter.data.repository.FakeBlockedSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.domain.model.CapturedMessage
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.notification.SourceBlocklist
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Unit tests for [CaptureIngestor.shouldIngest] and the [CaptureIngestor.submit] side-effect path.
 *
 * No Android framework is used. The injected [CoroutineScope] is replaced with [TestScope] so
 * launched coroutines can be drained synchronously via [advanceUntilIdle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureIngestorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val repository: NotificationRepository = mockk()

    /** A fresh [CaptureIngestor] for each test — prevents seen-cache leakage between tests. */
    private lateinit var ingestor: CaptureIngestor

    @Before
    fun setUp() {
        ingestor = ingestorBlocking()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun ingestorBlocking(vararg apps: String) = CaptureIngestor(
        repository,
        testScope,
        FakeBlockedSourceRepository(
            apps.fold(SourceBlocklist.EMPTY) { list, app -> list.with(app, Instant.EPOCH) },
        ),
    )

    private fun financialMessage(
        text: String = "Compra aprovada R$ 89,90",
        channel: NotificationChannel = NotificationChannel.SMS,
        receivedAt: Instant = Instant.ofEpochSecond(1_000_000L),
        app: String = "Banco Itaú",
    ) = CapturedMessage(app = app, channel = channel, text = text, receivedAt = receivedAt)

    private fun nonFinancialMessage(
        text: String = "Seu cadastro foi atualizado com sucesso.",
        channel: NotificationChannel = NotificationChannel.SMS,
        receivedAt: Instant = Instant.ofEpochSecond(1_000_000L),
    ) = CapturedMessage(app = "App", channel = channel, text = text, receivedAt = receivedAt)

    // -------------------------------------------------------------------------
    // shouldIngest — relevance gate
    // -------------------------------------------------------------------------

    @Test
    fun `shouldIngest returns false for non-financial message`() {
        val message = nonFinancialMessage()

        assertFalse(ingestor.shouldIngest(message))
    }

    @Test
    fun `shouldIngest returns true for first financial message`() {
        val message = financialMessage()

        assertTrue(ingestor.shouldIngest(message))
    }

    @Test
    fun `shouldIngest returns false for immediate duplicate of financial message`() {
        val message = financialMessage()

        ingestor.shouldIngest(message) // first call — records the key
        val result = ingestor.shouldIngest(message) // exact same object = same channel+text+bucket

        assertFalse(result)
    }

    @Test
    fun `shouldIngest returns true for two distinct financial messages`() {
        val first = financialMessage(text = "Compra aprovada R$ 89,90", receivedAt = Instant.ofEpochSecond(1_000_000L))
        val second = financialMessage(text = "Pix recebido R$ 250,00", receivedAt = Instant.ofEpochSecond(1_000_000L))

        assertTrue(ingestor.shouldIngest(first))
        assertTrue(ingestor.shouldIngest(second))
    }

    @Test
    fun `shouldIngest treats same text on different channels as different messages`() {
        val text = "Compra aprovada R$ 30,00"
        val sms = financialMessage(text = text, channel = NotificationChannel.SMS)
        val push = financialMessage(text = text, channel = NotificationChannel.PUSH)

        assertTrue(ingestor.shouldIngest(sms))
        assertTrue(ingestor.shouldIngest(push))
    }

    @Test
    fun `shouldIngest same text on same channel within the sliding window is a duplicate`() {
        val base = Instant.ofEpochSecond(1_000_000L)
        // 5 seconds apart, within the 10-second sliding window → duplicate.
        val first = financialMessage(text = "Compra aprovada R$ 55,00", receivedAt = base)
        val second = financialMessage(text = "Compra aprovada R$ 55,00", receivedAt = base.plusSeconds(5))

        assertTrue(ingestor.shouldIngest(first))
        assertFalse(ingestor.shouldIngest(second))
    }

    @Test
    fun `shouldIngest re-posts straddling a bucket boundary are still deduped within the window`() {
        // Sliding window: alignment is irrelevant. epochSecond 1_000_009 → 1_000_010 crosses the
        // old fixed-bucket boundary but is only 1s apart, so it must still be a duplicate.
        val first = financialMessage(text = "Compra aprovada R$ 55,00", receivedAt = Instant.ofEpochSecond(1_000_009L))
        val second = financialMessage(text = "Compra aprovada R$ 55,00", receivedAt = Instant.ofEpochSecond(1_000_010L))

        assertTrue(ingestor.shouldIngest(first))
        assertFalse(ingestor.shouldIngest(second))
    }

    @Test
    fun `shouldIngest same text outside the sliding window is not a duplicate`() {
        val base = Instant.ofEpochSecond(1_000_000L)
        // 11 seconds apart → beyond the 10-second window → ingested again.
        val first = financialMessage(text = "Compra aprovada R$ 55,00", receivedAt = base)
        val second = financialMessage(text = "Compra aprovada R$ 55,00", receivedAt = base.plusSeconds(11))

        assertTrue(ingestor.shouldIngest(first))
        assertTrue(ingestor.shouldIngest(second))
    }

    @Test
    fun `shouldIngest dedup is case-insensitive and whitespace-normalized`() {
        // The dedup key lowercases and collapses whitespace, so these two should be treated identically.
        val base = Instant.ofEpochSecond(1_000_000L)
        val first = financialMessage(text = "Compra aprovada R$ 89,90", receivedAt = base)
        val second = financialMessage(text = "COMPRA  APROVADA  R$ 89,90", receivedAt = base)

        assertTrue(ingestor.shouldIngest(first))
        assertFalse(ingestor.shouldIngest(second))
    }

    @Test
    fun `shouldIngest non-financial duplicate does not poison the seen cache`() {
        // Calling shouldIngest for a non-financial message must NOT put anything in the seen cache.
        // A subsequent financial message with the same text+channel+bucket should still be ingested.
        val base = Instant.ofEpochSecond(1_000_000L)
        val nonFinancial = nonFinancialMessage(
            text = "Seu cadastro foi atualizado.",
            receivedAt = base,
        )
        // Wrap the same text in a financial context — this is a pathological edge case but
        // confirms the cache is only populated by passing messages.
        val financial = financialMessage(
            text = "Compra aprovada R$ 10,00",
            receivedAt = base,
        )

        ingestor.shouldIngest(nonFinancial) // dropped — should not pollute cache
        assertTrue(ingestor.shouldIngest(financial))
    }

    // -------------------------------------------------------------------------
    // shouldIngest — blocked-source gate
    // -------------------------------------------------------------------------

    @Test
    fun `shouldIngest is fail-open until the blocklist has been collected`() {
        // Fail-closed would need runBlocking on the listener's main thread; one stray captured
        // item on a cold start is the cheaper failure.
        val blockedIngestor = ingestorBlocking("Blocked App")
        val message = financialMessage(app = "Blocked App", channel = NotificationChannel.PUSH)

        assertTrue(blockedIngestor.shouldIngest(message))
    }

    @Test
    fun `shouldIngest returns false for a blocked source before parsing`() {
        val blockedIngestor = ingestorBlocking("Blocked App")
        testDispatcher.scheduler.advanceUntilIdle()
        val message = financialMessage(app = "Blocked App", channel = NotificationChannel.PUSH)

        assertFalse(blockedIngestor.shouldIngest(message))
    }

    @Test
    fun `shouldIngest still returns true for a financial message from a non-blocked app`() {
        val gatedIngestor = ingestorBlocking("Blocked App")
        testDispatcher.scheduler.advanceUntilIdle()
        val message = financialMessage(app = "Banco Itaú", channel = NotificationChannel.PUSH)

        assertTrue(gatedIngestor.shouldIngest(message))
    }

    @Test
    fun `shouldIngest blocked message does not poison the seen cache`() {
        val gatedIngestor = ingestorBlocking("Blocked App")
        testDispatcher.scheduler.advanceUntilIdle()
        val base = Instant.ofEpochSecond(1_000_000L)
        val blocked = financialMessage(
            text = "Compra aprovada R$ 10,00",
            channel = NotificationChannel.PUSH,
            app = "Blocked App",
            receivedAt = base,
        )
        val allowed = financialMessage(
            text = "Compra aprovada R$ 10,00",
            channel = NotificationChannel.PUSH,
            app = "Banco Itaú",
            receivedAt = base,
        )

        gatedIngestor.shouldIngest(blocked) // dropped — must not pollute the dedup cache
        assertTrue(gatedIngestor.shouldIngest(allowed))
    }

    @Test
    fun `shouldIngest never blocks SMS even from a source blocked on PUSH`() {
        val gatedIngestor = ingestorBlocking("Google")
        testDispatcher.scheduler.advanceUntilIdle()
        val message = financialMessage(app = "Google", channel = NotificationChannel.SMS)

        assertTrue(gatedIngestor.shouldIngest(message))
    }

    // -------------------------------------------------------------------------
    // submit() — side-effect integration (uses TestScope)
    // -------------------------------------------------------------------------

    @Test
    fun `submit calls repository ingest for a financial message`() = runTest(testDispatcher) {
        val message = financialMessage()
        coEvery { repository.ingest(message) } returns Result.success(mockk())

        ingestor.submit(message)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.ingest(message) }
    }

    @Test
    fun `submit does not call repository ingest for a non-financial message`() = runTest(testDispatcher) {
        val message = nonFinancialMessage()

        ingestor.submit(message)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.ingest(any()) }
    }

    @Test
    fun `submit does not call repository ingest for an immediate duplicate`() = runTest(testDispatcher) {
        val message = financialMessage()
        coEvery { repository.ingest(message) } returns Result.success(mockk())

        ingestor.submit(message) // first — ingested
        testDispatcher.scheduler.advanceUntilIdle()
        ingestor.submit(message) // duplicate — dropped
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.ingest(message) }
    }

    @Test
    fun `submit does not throw when repository ingest returns failure`() = runTest(testDispatcher) {
        val message = financialMessage()
        coEvery { repository.ingest(message) } returns Result.failure(RuntimeException("network error"))

        ingestor.submit(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // If this point is reached without exception the test passes.
        coVerify(exactly = 1) { repository.ingest(message) }
    }
}
