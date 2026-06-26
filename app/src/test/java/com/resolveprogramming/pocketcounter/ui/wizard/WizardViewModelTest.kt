package com.resolveprogramming.pocketcounter.ui.wizard

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.Token
import com.resolveprogramming.pocketcounter.domain.model.TokenRole
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
    private val cardRepository: CardRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val seriesRepository: SeriesRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Default stubs — individual tests may override
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
        coEvery { seriesRepository.getAll() } returns Result.success(emptyList())
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.success(Series("s-new", "IFOOD", TransactionType.EXPENSE, null))
        coEvery { seriesRepository.setTags(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns Result.success(Unit)
        // Default: queue is empty after any save/ignore → onDone is called
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { notificationRepository.markIgnored(any()) } returns Result.success(Unit)
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
        paymentMethod: PaymentMethod? = null,
        cardId: String? = null,
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
            tagIds = tagIds,
            paymentMethod = paymentMethod,
            cardId = cardId,
        ),
        tokens = tokens,
    )

    private fun makeCreditCard(id: String = "card-x") = CreditCard(
        id = id,
        name = "Nubank",
        brand = "Mastercard",
        last4 = "1234",
        gradientStart = 0xFF6F00C9L,
        gradientEnd = 0xFF4A0096L,
        limit = BigDecimal("5000.00"),
        billDay = 10,
    )

    private fun makeViewModel(notificationId: String = "notif-1"): WizardViewModel {
        val handle = SavedStateHandle(mapOf("notificationId" to notificationId))
        return WizardViewModel(
            savedStateHandle = handle,
            notificationRepository = notificationRepository,
            cardRepository = cardRepository,
            tagRepository = tagRepository,
            transactionRepository = transactionRepository,
            seriesRepository = seriesRepository,
        )
    }

    // -------------------------------------------------------------------------
    // WizardStep enum — 4 steps only
    // -------------------------------------------------------------------------

    @Test
    fun `WizardStep has exactly 4 entries`() {
        assertEquals(4, WizardStep.entries.size)
    }

    @Test
    fun `WizardStep entries are TYPE AMOUNT PAYMENT TAGS in order`() {
        val entries = WizardStep.entries.map { it.name }
        assertEquals(listOf("TYPE", "AMOUNT", "PAYMENT", "TAGS"), entries)
    }

    @Test
    fun `WizardStep subtitles are 1 de 4 through 4 de 4`() {
        assertEquals("1 de 4", WizardStep.TYPE.subtitle)
        assertEquals("2 de 4", WizardStep.AMOUNT.subtitle)
        assertEquals("3 de 4", WizardStep.PAYMENT.subtitle)
        assertEquals("4 de 4", WizardStep.TAGS.subtitle)
    }

    // -------------------------------------------------------------------------
    // Short-circuit: classify returns pendingTransactionId != null
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification with pendingTransactionId sets isConfirmingPending true`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = "tx-pending-42")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()

        vm.state.test {
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
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = "tx-pending-42")
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()

        vm.state.test {
            awaitItem() // loading
            val ready = awaitItem()
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
        testDispatcher.scheduler.advanceUntilIdle()

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
    // Normal enrich: classify returns pendingTransactionId == null
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification normal enrich builds draft from classified notification`() = runTest {
        val enrichedNotification = makeNotification(
            status = NotificationStatus.NEEDS_REVIEW,
            type = TransactionType.EXPENSE,
            amount = BigDecimal("153.98"),
            paymentMethod = PaymentMethod.CREDIT,
            cardId = "card-abc",
            tagIds = listOf("tag-1"),
        )
        val classified = ClassifiedNotification(notification = enrichedNotification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(enrichedNotification)
        coEvery { notificationRepository.classify("notif-1", enrichedNotification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.EXPENSE, state.draft.type)
        assertEquals(BigDecimal("153.98"), state.draft.amount)
        assertEquals(PaymentMethod.CREDIT, state.draft.paymentMethod)
        assertEquals("card-abc", state.draft.cardId)
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

    @Test
    fun `loadNotification loads cards into state from cardRepository`() = runTest {
        val card = makeCreditCard("card-x")
        coEvery { cardRepository.getCards() } returns Result.success(listOf(card))
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(card), vm.state.value.cards)
    }

    // -------------------------------------------------------------------------
    // Graceful degrade: classify returns Result.failure
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification classify failure falls back to base notification draft`() = runTest {
        val base = makeNotification(
            type = TransactionType.INCOME,
            amount = BigDecimal("200.00"),
            paymentMethod = PaymentMethod.PIX,
        )
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(base)
        coEvery { notificationRepository.classify("notif-1", base) } returns Result.failure(RuntimeException("network error"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.state.value
        assertEquals(TransactionType.INCOME, state.draft.type)
        assertEquals(BigDecimal("200.00"), state.draft.amount)
        assertEquals(PaymentMethod.PIX, state.draft.paymentMethod)
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
    // save() path
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

        vm.save(onNext = {}, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markClassified("notif-1", "tx-new-99") }
    }

    @Test
    fun `save calls onDone after successful transaction save`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    @Test
    fun `save calls onDone even when markClassified returns failure (best-effort)`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new-99")
        coEvery { notificationRepository.markClassified("notif-1", "tx-new-99") } returns Result.failure(RuntimeException("server error"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    @Test
    fun `save does not call onDone when transactionRepository save fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(doneCalled)
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

        vm.save(onNext = {}, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("save failed", vm.state.value.error)
    }

    @Test
    fun `save resets isSaving to false on transaction save failure`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.save(onNext = {}, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isSaving)
    }

    // -------------------------------------------------------------------------
    // NEW: selectPaymentMethod
    // -------------------------------------------------------------------------

    @Test
    fun `selectPaymentMethod sets paymentMethod on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.PIX)

        assertEquals(PaymentMethod.PIX, vm.state.value.draft.paymentMethod)
    }

    @Test
    fun `selectPaymentMethod CREDIT then selectCard sets both on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectPaymentMethod(PaymentMethod.CREDIT)
        vm.selectCard("card-x")

        val draft = vm.state.value.draft
        assertEquals(PaymentMethod.CREDIT, draft.paymentMethod)
        assertEquals("card-x", draft.cardId)
    }

    @Test
    fun `selectPaymentMethod DEBIT after cardId was set clears cardId`() = runTest {
        val notification = makeNotification(paymentMethod = PaymentMethod.CREDIT, cardId = "card-x")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify card was loaded into draft
        assertEquals("card-x", vm.state.value.draft.cardId)

        vm.selectPaymentMethod(PaymentMethod.DEBIT)

        val draft = vm.state.value.draft
        assertEquals(PaymentMethod.DEBIT, draft.paymentMethod)
        assertNull(draft.cardId)
    }

    // -------------------------------------------------------------------------
    // NEW: selectCard
    // -------------------------------------------------------------------------

    @Test
    fun `selectCard sets cardId on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectCard("card-abc")

        assertEquals("card-abc", vm.state.value.draft.cardId)
    }

    // -------------------------------------------------------------------------
    // NEW: toggleFixo and updateRecurrenceDay
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFixo true then updateRecurrenceDay 10 both reflected in draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(10)

        val draft = vm.state.value.draft
        assertTrue(draft.isFixo)
        assertEquals(10, draft.recurrenceDay)
    }

    @Test
    fun `toggleFixo false clears isFixo on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.toggleFixo(false)

        assertFalse(vm.state.value.draft.isFixo)
    }

    @Test
    fun `updateRecurrenceDay null clears recurrenceDay on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.updateRecurrenceDay(15)

        vm.updateRecurrenceDay(null)

        assertNull(vm.state.value.draft.recurrenceDay)
    }

    // -------------------------------------------------------------------------
    // save() — recurring-series flow
    // -------------------------------------------------------------------------

    @Test
    fun `save non-fixo does not touch seriesRepository`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // draft.isFixo defaults to false — no toggleFixo call

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { seriesRepository.create(any(), any(), any()) }
        coVerify(exactly = 0) { seriesRepository.linkTransaction(any(), any(), any()) }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo with no existing series creates series then links transaction`() = runTest {
        val notification = makeNotification(tagIds = listOf("t1"))
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.create("IFOOD", TransactionType.EXPENSE, 10) } returns
            Result.success(Series("s-new", "IFOOD", TransactionType.EXPENSE, 10))
        coEvery { seriesRepository.setTags("s-new", listOf("t1")) } returns Result.success(Unit)
        coEvery { seriesRepository.linkTransaction("s-new", "tx-1", false) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(10)
        // draft.seriesId remains null — no selectSeries call

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            transactionRepository.save(any())
            seriesRepository.create("IFOOD", TransactionType.EXPENSE, 10)
            seriesRepository.setTags("s-new", listOf("t1"))
            seriesRepository.linkTransaction("s-new", "tx-1", false)
        }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo derives series name from merchant`() = runTest {
        // makeNotification sets merchantRaw = "IFOOD"; fromNotification seeds both merchant and name
        // from merchantRaw, so draft.merchant = "IFOOD" drives the series name via linkSeries
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        vm.save(onNext = {}, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.create("IFOOD", any(), any()) }
    }

    @Test
    fun `save fixo with empty tagIds does not call setTags`() = runTest {
        // notification has no suggested tagIds → draft.tagIds is empty
        val notification = makeNotification(tagIds = emptyList())
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        vm.save(onNext = {}, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { seriesRepository.setTags(any(), any()) }
    }

    @Test
    fun `save fixo linking an existing series links without creating`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.linkTransaction("s-existing", "tx-1", false) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)
        vm.selectSeries("s-existing")

        vm.save(onNext = {}, onDone = {})
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.linkTransaction("s-existing", "tx-1", false) }
        coVerify(exactly = 0) { seriesRepository.create(any(), any(), any()) }
        coVerify(exactly = 0) { seriesRepository.setTags(any(), any()) }
    }

    @Test
    fun `save fixo still succeeds when series create fails (best-effort, transaction not rolled back)`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.failure(RuntimeException("series create failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.save(any()) }
        coVerify(exactly = 1) { notificationRepository.markClassified(any(), any()) }
        assertTrue(doneCalled)
    }

    @Test
    fun `save fixo still succeeds when linkTransaction fails`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-1")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.success(Series("s-new", "IFOOD", TransactionType.EXPENSE, 5))
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns
            Result.failure(RuntimeException("link failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(true)
        vm.updateRecurrenceDay(5)

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    // -------------------------------------------------------------------------
    // save() — queue advancement
    // -------------------------------------------------------------------------

    @Test
    fun `save advances to next pending item when queue has another notification`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val nextNotification = makeNotification(id = "notif-2")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-99")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(nextNotification))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var nextId: String? = null
        var doneCalled = false
        vm.save(onNext = { nextId = it }, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("notif-2", nextId)
        assertFalse(doneCalled)
    }

    @Test
    fun `save calls onDone when no more pending notifications`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-99")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.save(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(doneCalled)
    }

    // -------------------------------------------------------------------------
    // ignore() path
    // -------------------------------------------------------------------------

    @Test
    fun `ignore marks notification ignored and advances to next pending item`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val nextNotification = makeNotification(id = "notif-2")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(nextNotification))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var nextId: String? = null
        var doneCalled = false
        vm.ignore(onNext = { nextId = it }, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertEquals("notif-2", nextId)
        assertFalse(doneCalled)
    }

    @Test
    fun `ignore calls onDone when no more pending notifications`() = runTest {
        val notification = makeNotification(id = "notif-1")
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        var doneCalled = false
        vm.ignore(onNext = {}, onDone = { doneCalled = true })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("notif-1") }
        assertTrue(doneCalled)
    }

    // -------------------------------------------------------------------------
    // updateName()
    // -------------------------------------------------------------------------

    @Test
    fun `updateName sets name and merchant on draft`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateName("Coffee Shop")

        val draft = vm.state.value.draft
        assertEquals("Coffee Shop", draft.name)
        assertEquals("Coffee Shop", draft.merchant)
    }

    @Test
    fun `updateName with blank value sets name to blank and clears merchant`() = runTest {
        val notification = makeNotification()
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.updateName("Coffee")
        vm.updateName("")

        val draft = vm.state.value.draft
        assertEquals("", draft.name)
        assertNull(draft.merchant)
    }

    // -------------------------------------------------------------------------
    // fromNotification — name seeded from merchantRaw (via initial state)
    // -------------------------------------------------------------------------

    @Test
    fun `loadNotification seeds draft name from notification merchantRaw`() = runTest {
        // makeNotification sets merchantRaw = "IFOOD"; fromNotification now seeds both
        // name and merchant from that field so the Descrição field opens pre-filled
        val notification = makeNotification() // merchantRaw = "IFOOD"
        val classified = ClassifiedNotification(notification = notification, pendingTransactionId = null)
        coEvery { notificationRepository.getById("notif-1") } returns Result.success(notification)
        coEvery { notificationRepository.classify("notif-1", notification) } returns Result.success(classified)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("IFOOD", vm.state.value.draft.name)
        assertEquals("IFOOD", vm.state.value.draft.merchant)
    }
}
