package com.resolveprogramming.pocketcounter.ui.home

import com.resolveprogramming.pocketcounter.data.local.LedgerRefreshSignal
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val notificationRepository: NotificationRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val cardRepository: CardRepository = mockk()
    private val tokenStore: TokenStore = mockk()

    private val currentMonth: YearMonth = YearMonth.now()
    private val currentDay: LocalDate = currentMonth.atDay(1).plusDays(2)

    private val expensePaid = HistoryItem(
        id = "exp-paid",
        date = currentDay,
        amount = BigDecimal("-100.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
    )
    private val expensePending = HistoryItem(
        id = "exp-pending",
        date = currentDay,
        amount = BigDecimal("-40.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PENDING,
    )
    private val income = HistoryItem(
        id = "inc-1",
        date = currentDay,
        amount = BigDecimal("300.00"),
        type = TransactionType.INCOME,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
    )

    private val monthItems = listOf(expensePaid, expensePending, income)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { cardRepository.getOpenInvoices(any()) } returns Result.success(emptyList())
        coEvery { tokenStore.getUserName() } returns "Guilherme"
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(monthItems)
        // Default: a pending item classifies to "not recognized" so confirmReady stays empty unless a
        // test opts in. getOrNull() on failure → null → filtered out.
        coEvery { notificationRepository.classify(any(), any()) } returns
            Result.failure(RuntimeException("not classified"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Fresh per VM so the process-scoped viewed month never leaks between tests.
    private fun makeViewModel(
        viewedMonth: ViewedMonthStore = ViewedMonthStore(),
        ledgerRefresh: LedgerRefreshSignal = LedgerRefreshSignal(),
    ): HomeViewModel = HomeViewModel(
        notificationRepository = notificationRepository,
        transactionRepository = transactionRepository,
        tagRepository = tagRepository,
        cardRepository = cardRepository,
        tokenStore = tokenStore,
        confirmClassifiedNotification = ConfirmClassifiedNotificationUseCase(
            transactionRepository,
            notificationRepository,
        ),
        viewedMonth = viewedMonth,
        ledgerRefresh = ledgerRefresh,
    )

    @Test
    fun `init loads current month KPIs`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        // expensePaid + expensePending are both expenses; income is the only INCOME row.
        assertEquals(2, s.kpis.expenseCount)
        assertEquals(1, s.kpis.incomeCount)
        assertEquals(BigDecimal("40.00"), s.kpis.pendingTotal)
        assertEquals(1, s.kpis.pendingCount)
        assertTrue(s.isCurrentMonth)
    }

    @Test
    fun `selectMonth off current month gates the pending banner`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isCurrentMonth)
        assertEquals(0, s.pendingReviewCount)
    }

    @Test
    fun `selectMonth reloads the month`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) { transactionRepository.getMonth(any()) }
        assertEquals(currentMonth.minusMonths(1), vm.state.value.month)
    }

    @Test
    fun `fatura tile reflects the selected month, not the current one`() = runTest {
        val currentRef = currentMonth.year * 100 + currentMonth.monthValue
        val prev = currentMonth.minusMonths(1)
        val prevRef = prev.year * 100 + prev.monthValue
        val currentInvoice = mockk<OpenInvoice> { every { total } returns BigDecimal("100.00") }
        val prevInvoice = mockk<OpenInvoice> { every { total } returns BigDecimal("777.00") }
        coEvery { cardRepository.getOpenInvoices(currentRef) } returns Result.success(listOf(currentInvoice))
        coEvery { cardRepository.getOpenInvoices(prevRef) } returns Result.success(listOf(prevInvoice))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(BigDecimal("100.00"), vm.state.value.openBillsTotal)

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        // The tile must follow the viewed month — it read the previous month's statement.
        assertEquals(BigDecimal("777.00"), vm.state.value.openBillsTotal)
        assertEquals(1, vm.state.value.openBillsCount)
        coVerify { cardRepository.getOpenInvoices(prevRef) }
    }

    @Test
    fun `ledger refresh signal reloads the month so Pendente stays fresh`() = runTest {
        val refresh = LedgerRefreshSignal()
        val vm = makeViewModel(ledgerRefresh = refresh)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.kpis.pendingCount)

        // A sibling screen (Transações) marks the pending expense paid, then broadcasts the change.
        coEvery { transactionRepository.getMonth(any()) } returns
            Result.success(listOf(expensePaid, income))
        refresh.signal()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.kpis.pendingCount)
        assertEquals(BigDecimal.ZERO, vm.state.value.kpis.pendingTotal)
    }

    @Test
    fun `setListType recomputes shown items and period total`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Default EXPENSE: two expense rows, total 140
        assertEquals(2, vm.state.value.shownItems.size)
        assertEquals(BigDecimal("140.00"), vm.state.value.periodTotal)

        vm.setListType(TransactionType.INCOME)

        assertEquals(1, vm.state.value.shownItems.size)
        assertEquals(BigDecimal("300.00"), vm.state.value.periodTotal)
        assertTrue(vm.state.value.shownItems.all { it.type == TransactionType.INCOME })
    }

    @Test
    fun `setGroupBy LISTA clears grouped sections`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setGroupBy(GroupMode.LISTA)
        assertTrue(vm.state.value.groupedSections.isEmpty())

        vm.setGroupBy(GroupMode.CONTEXTO)
        // Expenses with no tags fall into the "Sem categoria" bucket → one section.
        assertTrue(vm.state.value.groupedSections.isNotEmpty())
    }

    @Test
    fun `toggleStatus on paid item calls markPending and sets flashId`() = runTest {
        coEvery { transactionRepository.markPending("exp-paid") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleStatus(expensePaid)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.markPending("exp-paid") }
        coVerify(exactly = 0) { transactionRepository.markPaid(any()) }
        assertEquals("exp-paid", vm.state.value.flashId)
    }

    @Test
    fun `toggleStatus on pending item calls markPaid`() = runTest {
        coEvery { transactionRepository.markPaid("exp-pending") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleStatus(expensePending)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.markPaid("exp-pending") }
        coVerify(exactly = 0) { transactionRepository.markPending(any()) }
    }

    @Test
    fun `saveForm Add routes to save`() = runTest {
        coEvery { transactionRepository.save(any()) } returns Result.success("new-id")
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openAdd()
        assertEquals(FormMode.Add(), vm.state.value.formMode)

        vm.saveForm(WizardDraft(type = TransactionType.EXPENSE, amount = BigDecimal("10.00")))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.save(any()) }
        coVerify(exactly = 0) { transactionRepository.update(any(), any()) }
        assertEquals("new-id", vm.state.value.flashId)
        assertNull(vm.state.value.formMode)
    }

    @Test
    fun `saveForm Edit routes to update`() = runTest {
        coEvery { transactionRepository.update(eq("exp-paid"), any()) } returns Result.success("exp-paid")
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openEdit(expensePaid)
        vm.saveForm(WizardDraft(type = TransactionType.EXPENSE, amount = BigDecimal("10.00")))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.update(eq("exp-paid"), any()) }
        coVerify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun `empty month sets isEmptyMonth and zero KPIs`() = runTest {
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(emptyList())
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.isEmptyMonth)
        assertEquals(0, s.kpis.expenseCount)
        assertEquals(0, s.kpis.incomeCount)
        assertEquals(0, s.kpis.pendingCount)
        assertEquals(BigDecimal.ZERO, s.balance)
        assertTrue(s.shownItems.isEmpty())
    }

    @Test
    fun `consumeFlash clears flashId`() = runTest {
        coEvery { transactionRepository.markPending(any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleStatus(expensePaid)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("exp-paid", vm.state.value.flashId)

        vm.consumeFlash()
        assertNull(vm.state.value.flashId)
    }

    // =========================================================================
    // monthCount (C from architect plan)
    // =========================================================================

    @Test
    fun `monthCount equals number of items in the month`() = runTest {
        // monthItems has 3 items: expensePaid, expensePending, income
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.monthCount)
    }

    @Test
    fun `monthCount is zero for empty month`() = runTest {
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.monthCount)
    }

    // =========================================================================
    // pendingReviewFirstId — teaching-wizard entry from the Revisar banner
    // =========================================================================

    private fun pendingNotification(id: String): NotificationItem =
        mockk(relaxed = true) { every { this@mockk.id } returns id }

    @Test
    fun `pendingReviewFirstId is the first pending id on current month`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(pendingNotification("pend-1"), pendingNotification("pend-2")),
        )
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.pendingReviewCount)
        assertEquals("pend-1", vm.state.value.pendingReviewFirstId)
    }

    @Test
    fun `pendingReviewFirstId is null when no pendencies`() = runTest {
        // setUp already stubs getPendingReview() to empty.
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.pendingReviewCount)
        assertNull(vm.state.value.pendingReviewFirstId)
    }

    @Test
    fun `pendingReviewFirstId is null off the current month`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(pendingNotification("pend-1")),
        )
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.pendingReviewCount)
        assertNull(vm.state.value.pendingReviewFirstId)
    }

    @Test
    fun `refresh clears pending banner when list becomes empty`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns Result.success(
            listOf(pendingNotification("pend-1")),
        )
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("pend-1", vm.state.value.pendingReviewFirstId)

        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.pendingReviewCount)
        assertNull(vm.state.value.pendingReviewFirstId)
    }

    // -------------------------------------------------------------------------
    // Confirm-ready: per-item classification + one-tap confirm
    // -------------------------------------------------------------------------

    private fun recognizedNotification(id: String): NotificationItem = NotificationItem(
        id = id,
        app = "App",
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        text = "Compra aprovada DL*UberRides",
        status = NotificationStatus.AUTO,
        parsed = ParsedNotification(
            type = TransactionType.EXPENSE,
            amount = BigDecimal("26.74"),
            date = currentDay,
            merchantRaw = "DL*UberRides",
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(
            tagIds = listOf("t1"),
            paymentMethod = PaymentMethod.PIX,
            cardId = null,
        ),
        tokens = emptyList(),
    )

    @Test
    fun `classify surfaces a recognized notification as confirm-ready`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = vm.state.value.confirmReady
        assertEquals(1, ready.size)
        assertEquals("pend-1", ready.single().notificationId)
        // The banner counts only items still needing the wizard.
        assertEquals(0, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `confirm-ready classify is capped at ten per load`() = runTest {
        val many = (1..12).map { recognizedNotification("p$it") }
        coEvery { notificationRepository.getPendingReview() } returns Result.success(many)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 10) { notificationRepository.classify(any(), any()) }
    }

    @Test
    fun `confirm saves the transaction, toasts, and removes the card`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        // After confirming, the notification leaves the pending queue.
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.save(any()) }
        coVerify { notificationRepository.markClassified("pend-1", "tx-new") }
        assertEquals("Transação confirmada", vm.state.value.toastMessage)
        assertTrue(vm.state.value.confirmReady.isEmpty())
    }

    @Test
    fun `confirm of a pending-transaction match marks it paid instead of creating`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = "tx-99"))
        coEvery { transactionRepository.markPaid("tx-99") } returns Result.success(Unit)
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.markPaid("tx-99") }
        coVerify(exactly = 0) { transactionRepository.save(any()) }
        assertEquals("Transação confirmada", vm.state.value.toastMessage)
    }

    @Test
    fun `confirm failure restores the card and toasts`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("save failed"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.confirmReady.size)
        assertEquals("pend-1", vm.state.value.confirmReady.single().notificationId)
        assertTrue(vm.state.value.confirmingIds.isEmpty())
        assertEquals("Não foi possível confirmar", vm.state.value.toastMessage)
    }

    // -------------------------------------------------------------------------
    // App-wide viewed month — Home shares it with Transações/Cartões/Resumo
    // -------------------------------------------------------------------------

    @Test
    fun `Home adopts the shared viewed month on start`() = runTest {
        val store = ViewedMonthStore()
        store.set(currentMonth.minusMonths(2).toString())
        val vm = makeViewModel(store)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(currentMonth.minusMonths(2), vm.state.value.month)
        assertFalse(vm.state.value.isCurrentMonth)
    }

    @Test
    fun `Home reacts when another screen steps the shared month`() = runTest {
        val store = ViewedMonthStore()
        val vm = makeViewModel(store)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(currentMonth, vm.state.value.month)

        // Simulate Transações/Cartões stepping forward.
        store.step(1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(currentMonth.plusMonths(1), vm.state.value.month)
    }

    @Test
    fun `selectMonth writes through to the shared store`() = runTest {
        val store = ViewedMonthStore()
        val vm = makeViewModel(store)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(currentMonth.minusMonths(1).toString(), store.month.value)
        assertEquals(currentMonth.minusMonths(1), vm.state.value.month)
    }

    // =========================================================================
    // Manual refresh — isRefreshing flag + fail-soft + toast + concurrency guard
    // =========================================================================

    @Test
    fun `onManualRefresh reloads the month`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onManualRefresh()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) { transactionRepository.getMonth(any()) }
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `onManualRefresh success clears isRefreshing and shows no error toast`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onManualRefresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isRefreshing)
        // A successful refresh must not surface the connection-error toast.
        assertNull(vm.state.value.toastMessage)
    }

    @Test
    fun `onManualRefresh holds isRefreshing for a minimum duration on an instant reload`() = runTest {
        // getMonth resolves instantly (default success stub). Without a minimum hold, isRefreshing
        // would flip true->false within a frame and strand PullToRefreshBox in a re-trigger loop.
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onManualRefresh()
        testDispatcher.scheduler.runCurrent()          // commit isRefreshing=true, run the instant reload
        testDispatcher.scheduler.advanceTimeBy(200)     // well under the 600ms floor
        assertTrue(vm.state.value.isRefreshing)         // still up despite the reload already finishing

        testDispatcher.scheduler.advanceUntilIdle()     // let the floor elapse
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `onManualRefresh failure toasts and keeps content`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // Initial load succeeded: 3 items
        assertEquals(3, vm.state.value.monthCount)

        coEvery { transactionRepository.getMonth(any()) } returns Result.failure(RuntimeException("network error"))
        vm.onManualRefresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sem conexão. Tente novamente.", vm.state.value.toastMessage)
        // fail-soft: existing content is NOT blanked
        assertEquals(3, vm.state.value.monthCount)
        assertEquals(2, vm.state.value.shownItems.size)
    }

    @Test
    fun `onManualRefresh ignores a concurrent call while refreshing`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Gate getMonth so the first onManualRefresh stays truly in-flight during runCurrent().
        // Without this, MockK's stub returns synchronously and runCurrent() collapses the entire
        // refresh cycle — setting isRefreshing back to false — before the second call checks it.
        val latch = CompletableDeferred<Result<List<HistoryItem>>>()
        coEvery { transactionRepository.getMonth(any()) } coAnswers { latch.await() }

        vm.onManualRefresh()
        // runCurrent advances past isRefreshing=true and into the suspended getMonth call,
        // but cannot proceed past latch.await() — so isRefreshing is still true here.
        testDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.isRefreshing)

        vm.onManualRefresh() // second call — ignored because isRefreshing is true
        latch.complete(Result.success(monthItems)) // release the first refresh
        testDispatcher.scheduler.advanceUntilIdle()

        // 1 init call + 1 manual call; the second onManualRefresh was a no-op
        coVerify(exactly = 2) { transactionRepository.getMonth(any()) }
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `refresh (ON_RESUME) stays silent on failure`() = runTest {
        coEvery { transactionRepository.getMonth(any()) } returns Result.failure(RuntimeException("network"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.isRefreshing)
        assertTrue(vm.state.value.toastMessage != "Sem conexão. Tente novamente.")
    }
}
