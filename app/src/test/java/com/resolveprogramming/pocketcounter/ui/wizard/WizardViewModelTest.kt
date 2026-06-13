package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.PaymentSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentSource
import com.resolveprogramming.pocketcounter.domain.model.PaymentSourceKind
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WizardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val notificationRepository: NotificationRepository = mockk()
    private val paymentSourceRepository: PaymentSourceRepository = mockk()
    private val sourceRepository: SourceRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default stubs that most tests rely on — individual tests may override
        coEvery { paymentSourceRepository.getAll() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
        coEvery { sourceRepository.getByPaymentSourceAndType(any(), any()) } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeNotification(
        id: String = "notif-1",
        status: NotificationStatus = NotificationStatus.NEEDS_REVIEW,
        type: TransactionType? = TransactionType.EXPENSE,
        amount: BigDecimal? = BigDecimal("49.90"),
        idPaymentSource: String? = "itau",
        idSource: String? = "src-ifood",
        tagIds: List<String> = emptyList(),
        tokens: List<Token> = emptyList(),
    ) = NotificationItem(
        id = id,
        app = "Banco Itaú",
        channel = NotificationChannel.SMS,
        time = "agora",
        received = "10:00",
        text = "Compra aprovada R$ 49,90",
        status = status,
        parsed = ParsedNotification(
            type = type,
            amount = amount,
            date = LocalDate.of(2026, 6, 12),
            merchantRaw = "IFOOD",
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(
            idPaymentSource = idPaymentSource,
            idSource = idSource,
            tagIds = tagIds,
        ),
        tokens = tokens,
    )

    private fun makeViewModel(notificationId: String = "notif-1"): WizardViewModel {
        val handle = SavedStateHandle(mapOf("notificationId" to notificationId))
        return WizardViewModel(
            savedStateHandle = handle,
            notificationRepository = notificationRepository,
            paymentSourceRepository = paymentSourceRepository,
            sourceRepository = sourceRepository,
            tagRepository = tagRepository,
            transactionRepository = transactionRepository,
        )
    }

    // -------------------------------------------------------------------------
    // Priority 2a — short-circuit: classify returns pendingTransactionId != null
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification with pendingTransactionId sets isConfirmingPending true`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(
            notification = notification,
            pendingTransactionId = "tx-pending-42",
        )
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()

        vm.state.test {
            // skip initial loading state
            val loading = awaitItem()
            assertTrue(loading.isLoading)

            val ready = awaitItem()
            assertTrue(ready.isConfirmingPending)
            assertEquals("tx-pending-42", ready.pendingTransactionId)
            assertFalse(ready.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loadNotification with pendingTransactionId does not build a draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(
            notification = notification,
            pendingTransactionId = "tx-pending-42",
        )
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()

        vm.state.test {
            awaitItem() // loading
            val ready = awaitItem()
            // When confirming pending, draft stays at its zero-value default
            assertNull(ready.draft.type)
            assertNull(ready.draft.amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `confirmPending calls markPaid then markClassified and sets pendingConfirmed`() = runTest {
        val notification = makeNotification()
        val pendingId = "tx-pending-42"
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = pendingId)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.markPaid(pendingId) } returns Result.success(Unit)
        coEvery { notificationRepository.markClassified("notif-1", pendingId) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle() // finish loadNotification

        vm.confirmPending()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.pendingConfirmed)
        assertFalse(state.isConfirmingPending)
        coVerify(exactly = 1) { transactionRepository.markPaid(pendingId) }
        coVerify(exactly = 1) { notificationRepository.markClassified("notif-1", pendingId) }
    }

    @Test
    fun `confirmPending does nothing when pendingTransactionId is null`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.confirmPending()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { transactionRepository.markPaid(any()) }
        assertFalse(vm.state.value.isSuccess)
    }

    // -------------------------------------------------------------------------
    // Priority 2a — normal enrich: classify returns pendingTransactionId == null
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification normal enrich builds draft from classified notification`() = runTest {
        val enrichedNotification = makeNotification(
            status = NotificationStatus.NEEDS_REVIEW,
            type = TransactionType.EXPENSE,
            amount = BigDecimal("153.98"),
            idPaymentSource = "itau",
            idSource = "src-pao",
            tagIds = listOf("tag-1"),
        )
        val classified = ClassifiedNotification(notification = enrichedNotification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(enrichedNotification)
        coEvery { notificationRepository.classify("notif-1", enrichedNotification) } returns Result.success(classified)
        coEvery { sourceRepository.getByPaymentSourceAndType("itau", TransactionType.EXPENSE) } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.EXPENSE, state.draft.type)
        assertEquals(BigDecimal("153.98"), state.draft.amount)
        assertEquals("itau", state.draft.idPaymentSource)
        assertEquals("src-pao", state.draft.idSource)
        assertEquals(listOf("tag-1"), state.draft.tagIds)
    }

    @Test
    fun `loadNotification normal enrich populates tokens from classified notification`() = runTest {
        val tokenList = listOf(
            Token(text = "Compra"),
            Token(text = "49,90", role = TokenRole.AMOUNT, value = "49.90"),
        )
        val enrichedNotification = makeNotification(tokens = tokenList)
        val classified = ClassifiedNotification(notification = enrichedNotification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(enrichedNotification)
        coEvery { notificationRepository.classify("notif-1", enrichedNotification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(tokenList, vm.state.value.tokens)
    }

    @Test
    fun `loadNotification NEEDS_TAGS status resolves start step to TAGS`() = runTest {
        val notification = makeNotification(status = NotificationStatus.NEEDS_TAGS)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WizardStep.TAGS, vm.state.value.step)
    }

    @Test
    fun `loadNotification non-NEEDS_TAGS status resolves start step to TYPE`() = runTest {
        val notification = makeNotification(status = NotificationStatus.NEEDS_REVIEW)
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(WizardStep.TYPE, vm.state.value.step)
    }

    @Test
    fun `loadNotification sets isLoading false after enrich`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `loadNotification normal enrich does not set isConfirmingPending`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isConfirmingPending)
    }

    // -------------------------------------------------------------------------
    // Priority 2a — graceful degrade: classify returns Result.failure
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification classify failure falls back to base notification draft`() = runTest {
        val base = makeNotification(
            type = TransactionType.INCOME,
            amount = BigDecimal("200.00"),
            idPaymentSource = "nubank",
            idSource = "src-salary",
        )
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("network error"))
        coEvery { sourceRepository.getByPaymentSourceAndType("nubank", TransactionType.INCOME) } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.INCOME, state.draft.type)
        assertEquals(BigDecimal("200.00"), state.draft.amount)
        assertEquals("nubank", state.draft.idPaymentSource)
        assertEquals("src-salary", state.draft.idSource)
    }

    @Test
    fun `loadNotification classify failure sets non-null error message`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("classify failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.error)
        assertEquals("classify failed", vm.state.value.error)
    }

    @Test
    fun `loadNotification classify failure does not set isConfirmingPending`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("classify failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isConfirmingPending)
    }

    @Test
    fun `loadNotification classify failure still sets isLoading false`() = runTest {
        val base = makeNotification()
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("timeout"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
    }

    // -------------------------------------------------------------------------
    // Priority 2a — save() path
    // -------------------------------------------------------------------------

    @Test
    fun `save calls markClassified after successful transaction save`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markClassified("notif-1", "tx-new-99") }
    }

    @Test
    fun `save sets isSuccess true after successful transaction save`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isSuccess)
    }

    @Test
    fun `save sets isSuccess true even when markClassified returns failure (best-effort)`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
        // markClassified returns failure — the VM ignores the return value, so isSuccess must still be true
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.failure(RuntimeException("server error"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.isSuccess)
    }

    @Test
    fun `save does not set isSuccess when transactionRepository save fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isSuccess)
    }

    @Test
    fun `save sets error when transactionRepository save fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("save failed", vm.state.value.error)
    }

    @Test
    fun `save sets isSaving false after completion`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-99")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isSaving)
    }
}
