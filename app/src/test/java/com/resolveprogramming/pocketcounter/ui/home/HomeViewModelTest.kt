package com.resolveprogramming.pocketcounter.ui.home

import com.resolveprogramming.pocketcounter.data.local.AppMessageRelay
import com.resolveprogramming.pocketcounter.data.local.LedgerRefreshSignal
import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.FakeBlockedSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.FakeIssuerCardRepository
import com.resolveprogramming.pocketcounter.data.repository.IssuerCardRepository
import com.resolveprogramming.pocketcounter.data.repository.FakeProductiveSourceRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.ClassificationSuggestion
import com.resolveprogramming.pocketcounter.domain.model.ClassifiedNotification
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationChannel
import com.resolveprogramming.pocketcounter.domain.model.NotificationItem
import com.resolveprogramming.pocketcounter.domain.model.NotificationStatus
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.ParsedNotification
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.domain.usecase.ConfirmClassifiedNotificationUseCase
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val notificationRepository: NotificationRepository = mockk()
    private val transactionRepository: TransactionRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val cardRepository: CardRepository = mockk()
    private val issuerCardRepository = FakeIssuerCardRepository()
    private val blockedSourceRepository = FakeBlockedSourceRepository()
    private val productiveSourceRepository = FakeProductiveSourceRepository()
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
        every { tagRepository.refreshLookups() } returns Unit
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
        issuerCardRepository: IssuerCardRepository = this.issuerCardRepository,
        appMessageRelay: AppMessageRelay = AppMessageRelay(),
    ): HomeViewModel = HomeViewModel(
        notificationRepository = notificationRepository,
        transactionRepository = transactionRepository,
        tagRepository = tagRepository,
        cardRepository = cardRepository,
        issuerCardRepository = issuerCardRepository,
        tokenStore = tokenStore,
        confirmClassifiedNotification = ConfirmClassifiedNotificationUseCase(
            transactionRepository,
            notificationRepository,
        ),
        viewedMonth = viewedMonth,
        ledgerRefresh = ledgerRefresh,
        appMessageRelay = appMessageRelay,
        blockedSourceRepository = blockedSourceRepository,
        productiveSourceRepository = productiveSourceRepository,
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
    fun `a relayed message lands in toastMessage so a popped screen's feedback still renders`() = runTest {
        val relay = AppMessageRelay()
        val vm = makeViewModel(appMessageRelay = relay)
        testDispatcher.scheduler.advanceUntilIdle()

        // The wizard signs off as its review queue empties, then pops back to Home.
        relay.send("Notificações do Google não serão mais capturadas.")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Notificações do Google não serão mais capturadas.", vm.state.value.toastMessage)
    }

    @Test
    fun `blockedSourceCount follows the blocklist`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, vm.state.value.blockedSourceCount)

        blockedSourceRepository.block("Google", Instant.now())
        blockedSourceRepository.block("Promo Bank", Instant.now())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, vm.state.value.blockedSourceCount)
    }

    @Test
    fun `blockedSourceCount returns to zero after the last unblock`() = runTest {
        blockedSourceRepository.block("Google", Instant.now())
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, vm.state.value.blockedSourceCount)

        blockedSourceRepository.unblock("google")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, vm.state.value.blockedSourceCount)
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
    fun `confirm records the notification's source as productive`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { transactionRepository.save(any()) } returns Result.success("tx-new")
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, productiveSourceRepository.countFor("App"))
    }

    @Test
    fun `a failed confirm does not record the source as productive`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { transactionRepository.save(any()) } returns Result.failure(RuntimeException("boom"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, productiveSourceRepository.countFor("App"))
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

    @Test
    fun `ignore removes only that card, marks it ignored, keeps count, emits no ledger signal`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { notificationRepository.markIgnored("pend-1") } returns Result.success(Unit)
        val refresh = LedgerRefreshSignal()
        var signals = 0
        backgroundScope.launch { refresh.events.collect { signals++ } }
        val vm = makeViewModel(ledgerRefresh = refresh)
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()
        val countBefore = vm.state.value.pendingReviewCount

        vm.ignore(item)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("pend-1") }
        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertEquals("Notificação ignorada", vm.state.value.toastMessage)
        // Ignore is a notification-state change, not a ledger mutation:
        assertEquals(countBefore, vm.state.value.pendingReviewCount)
        assertEquals(0, signals)
    }

    @Test
    fun `ignore failure restores the card and toasts`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { notificationRepository.markIgnored("pend-1") } returns
            Result.failure(RuntimeException("ignore failed"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.ignore(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.confirmReady.size)
        assertEquals("pend-1", vm.state.value.confirmReady.single().notificationId)
        assertTrue(vm.state.value.confirmingIds.isEmpty())
        assertEquals("Não foi possível ignorar", vm.state.value.toastMessage)
    }

    // -------------------------------------------------------------------------
    // Invoice payment: "Recebemos seu pagamento" must settle the invoice, not duplicate it
    // -------------------------------------------------------------------------

    private fun invoiceRow(id: String, amount: String, name: String, cardId: String = "card-1") = HistoryItem(
        id = id,
        date = currentDay,
        amount = BigDecimal(amount),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PENDING,
        paymentMethod = PaymentMethod.CREDIT,
        cardId = cardId,
        name = name,
        isInvoice = true,
    )

    private fun invoicePaymentNotification(
        id: String,
        amount: String = "8866.19",
        status: NotificationStatus = NotificationStatus.NEEDS_REVIEW,
        parsedType: TransactionType? = null,
        app: String = "Nubank",
    ) = NotificationItem(
        id = id,
        app = app,
        channel = NotificationChannel.PUSH,
        time = "agora",
        received = "2026-06-30T13:25:00Z",
        text = "Nubank Recebemos seu pagamento no valor de R$ 8.866,19. Obrigado!",
        status = status,
        parsed = ParsedNotification(
            type = parsedType,
            amount = BigDecimal(amount),
            date = currentDay,
            merchantRaw = null,
            paymentHint = null,
        ),
        suggestions = ClassificationSuggestion(tagIds = emptyList()),
        tokens = emptyList(),
    )

    private fun stubInvoicePush(vararg rows: HistoryItem, notificationId: String = "pend-inv") {
        val notification = invoicePaymentNotification(notificationId)
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))
        coEvery { notificationRepository.classify(notificationId, any()) } returns
            Result.success(ClassifiedNotification(notification, pendingTransactionId = null))
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(monthItems + rows)
        coEvery { notificationRepository.markClassified(any(), any()) } returns Result.success(Unit)
    }

    @Test
    fun `confirming a matched invoice payment marks it paid and never saves`() = runTest {
        stubInvoicePush(invoiceRow("inv-1", "-8866.19", "Fatura Nubank"))
        coEvery { transactionRepository.markPaid("inv-1") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = vm.state.value.confirmReady.single()
        assertEquals("inv-1", item.pendingTransactionId)
        assertEquals("Fatura Nubank", item.pendingMatch?.displayTitle())

        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.markPaid("inv-1") }
        // The whole point of the feature: confirming must never create a second expense.
        coVerify(exactly = 0) { transactionRepository.save(any()) }
    }

    @Test
    fun `confirming a one-tap matched invoice payment toasts which invoice was marked paid`() = runTest {
        stubInvoicePush(invoiceRow("inv-1", "-8866.19", "Fatura Nubank"))
        coEvery { transactionRepository.markPaid("inv-1") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.confirm(item)
        testDispatcher.scheduler.advanceUntilIdle()

        // The generic "Transação confirmada" asserts a transaction was created — wrong for a path
        // that marks an existing invoice paid instead.
        assertEquals("Fatura Nubank marcada como paga ✓", vm.state.value.toastMessage)
    }

    @Test
    fun `ignoring a one-tap matched invoice payment forgets the learned issuer, since there is no other undo for a wrong auto-match`() = runTest {
        // Two same-amount invoices on different cards: the learned mapping is what narrows the
        // ambiguity down to inv-1, not the amount alone — exactly the case viaLearnedIssuer exists
        // to flag, unlike a single unambiguous exact match the map merely corroborates.
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10)),
        )
        issuerCardRepository.associate("Nubank", "card-1")
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank", cardId = "card-1"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior", cardId = "card-2"),
        )
        coEvery { notificationRepository.markIgnored("pend-inv") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.ignore(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(issuerCardRepository.getMap().isEmpty())
    }

    @Test
    fun `ignoring a match learned via the leading token forgets that entry, not the app label, for an SMS aggregator delivery`() = runTest {
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10)),
        )
        issuerCardRepository.associate("Nubank", "card-1")
        val notification = invoicePaymentNotification("pend-inv", app = "Mensagens")
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))
        coEvery { notificationRepository.classify("pend-inv", any()) } returns
            Result.success(ClassifiedNotification(notification, pendingTransactionId = null))
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(
            monthItems +
                invoiceRow("inv-1", "-8866.19", "Fatura Nubank", cardId = "card-1") +
                invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior", cardId = "card-2"),
        )
        coEvery { notificationRepository.markIgnored("pend-inv") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.ignore(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(issuerCardRepository.getMap().isEmpty())
    }

    @Test
    fun `ignoring a plain recognized notification leaves any learned issuer mapping alone`() = runTest {
        issuerCardRepository.associate("Nubank", "card-1")
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("pend-1")))
        coEvery { notificationRepository.classify("pend-1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("pend-1"), pendingTransactionId = null))
        coEvery { notificationRepository.markIgnored("pend-1") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.ignore(item)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("nubank" to "card-1"), issuerCardRepository.getMap())
    }

    @Test
    fun `ignoring an invoice payment matched purely by amount never touches the learned map`() = runTest {
        // No card on file at all, so this match resolved by amount alone — the learned map had no
        // part in it. Clearing anything here would be a no-op at best and a needless write at worst;
        // the point is that ignore() must not even attempt it.
        val issuerRepo: IssuerCardRepository = mockk(relaxed = true)
        coEvery { issuerRepo.getMap() } returns emptyMap()
        stubInvoicePush(invoiceRow("inv-1", "-8866.19", "Fatura Nubank"))
        coEvery { notificationRepository.markIgnored("pend-inv") } returns Result.success(Unit)
        val vm = makeViewModel(issuerCardRepository = issuerRepo)
        testDispatcher.scheduler.advanceUntilIdle()
        val item = vm.state.value.confirmReady.single()

        vm.ignore(item)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { issuerRepo.clear(any()) }
    }

    @Test
    fun `a legacy row with parsedType EXPENSE and status AUTO still matches the invoice by text`() = runTest {
        // parsedType is stored server-side verbatim with no update endpoint, so a row captured
        // before this feature shipped still carries EXPENSE/AUTO even though its text is an
        // invoice-payment confirmation. The match must be decided by text, not by that stale field.
        val notification = invoicePaymentNotification(
            "pend-inv",
            status = NotificationStatus.AUTO,
            parsedType = TransactionType.EXPENSE,
        )
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))
        coEvery { notificationRepository.classify("pend-inv", any()) } returns
            Result.success(ClassifiedNotification(notification, pendingTransactionId = null))
        coEvery { transactionRepository.getMonth(any()) } returns
            Result.success(monthItems + invoiceRow("inv-1", "-8866.19", "Fatura Nubank"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val item = vm.state.value.confirmReady.single()
        assertEquals("inv-1", item.pendingTransactionId)
    }

    @Test
    fun `two invoices with the same amount produce a prompt and no confirm-ready card`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertEquals(1, vm.state.value.invoicePrompts.size)
        assertEquals(2, vm.state.value.invoicePrompts.single().candidates.size)
        // A prompt is handled, so it must not also sit in the wizard-path banner.
        assertEquals(0, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `a failed neighbour-month fetch skips invoice matching instead of matching off a truncated window`() = runTest {
        // Two same-amount pending invoices, one this month and one next month. If the next-month
        // fetch failed silently, the matcher would only ever see the current-month one and
        // auto-resolve a Matched — the exact truncated-window false confidence this guards against.
        val notification = invoicePaymentNotification("pend-inv")
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))
        coEvery { notificationRepository.classify("pend-inv", any()) } returns
            Result.success(ClassifiedNotification(notification, pendingTransactionId = null))
        coEvery { transactionRepository.getMonth(currentMonth.toString()) } returns
            Result.success(monthItems + invoiceRow("inv-1", "-8866.19", "Fatura Nubank"))
        coEvery { transactionRepository.getMonth(currentMonth.minusMonths(1).toString()) } returns
            Result.success(emptyList())
        coEvery { transactionRepository.getMonth(currentMonth.plusMonths(1).toString()) } returns
            Result.failure(RuntimeException("offline"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals(1, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `invoice matching waits for cards to load instead of matching with an empty issuer veto`() = runTest {
        // The push names Nubank, and the pending invoice is on a DIFFERENT card — the issuer must
        // veto the amount match. With cards still loading, IssuerCardMatcher has nothing to resolve
        // against and the veto can't fire, so an unheld match would auto-resolve wrong.
        val cardsDeferred = CompletableDeferred<Result<List<CreditCard>>>()
        coEvery { cardRepository.getCards() } coAnswers { cardsDeferred.await() }
        stubInvoicePush(invoiceRow("inv-1", "-8866.19", "Fatura Nubank", cardId = "card-2"))
        val vm = makeViewModel()
        testDispatcher.scheduler.runCurrent()

        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertTrue(vm.state.value.invoicePrompts.isEmpty())

        cardsDeferred.complete(
            Result.success(listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10))),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertEquals(1, vm.state.value.invoicePrompts.size)
    }

    @Test
    fun `a failed card load must not silently disable the issuer veto`() = runTest {
        // Same shape as the "cards still loading" case above: the push names Nubank, and the
        // pending invoice is on a DIFFERENT card. A failed getCards() must not degrade to an empty
        // cards map that lets the amount match auto-resolve unvetoed.
        coEvery { cardRepository.getCards() } returns Result.failure(RuntimeException("network"))
        stubInvoicePush(invoiceRow("inv-1", "-8866.19", "Fatura Nubank", cardId = "card-2"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals(1, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `a learned-issuer read failure must not resolve a match that depends on the missing mapping`() = runTest {
        val failingIssuerCardRepository: IssuerCardRepository = mockk()
        coEvery { failingIssuerCardRepository.getMap() } throws RuntimeException("datastore error")
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(
                CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
                CreditCard("card-2", "Roxinho", "Mastercard", "1111", 0L, 0L, BigDecimal("1000"), 10),
            ),
        )
        // Previously taught: this issuer's real invoices land on card-2 ("Roxinho"), not the
        // literally-named "Nubank" card — exactly what the learned map exists to remember.
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank", cardId = "card-1"),
            invoiceRow("inv-2", "-8866.19", "Fatura Roxinho", cardId = "card-2"),
        )
        val vm = makeViewModel(issuerCardRepository = failingIssuerCardRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Without the learned mapping, a name-only match would auto-settle the wrong invoice
        // (card-1's) instead of falling through to the picker or the revisar banner.
        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals(1, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `picking an invoice marks it paid, learns the issuer, and never saves`() = runTest {
        // A card literally named "Nubank" so resolutionKey has a real signal to teach on — refusing
        // to teach a fallback key (neither app nor leading token names a card) is deliberate.
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10)),
        )
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        coEvery { transactionRepository.markPaid("inv-2") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        // After confirming, the notification leaves the pending queue.
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first { it.id == "inv-2" })
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.markPaid("inv-2") }
        coVerify(exactly = 0) { transactionRepository.save(any()) }
        // Normalized: production's IssuerCardMatcher.resolve looks the learned map up by
        // normalizeIssuer(app), never by the raw app label.
        assertEquals(mapOf("nubank" to "card-1"), issuerCardRepository.getMap())
        assertNull(vm.state.value.invoicePicker)
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals("Fatura Nubank anterior marcada como paga ✓", vm.state.value.toastMessage)
    }

    @Test
    fun `picking an invoice records the source as productive`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        coEvery { transactionRepository.markPaid("inv-2") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first { it.id == "inv-2" })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, productiveSourceRepository.countFor("Nubank"))
    }

    @Test
    fun `picking an invoice from an SMS aggregator delivery teaches the leading token, not the app label`() = runTest {
        // app = "Mensagens" names no card; the issuer is only findable via the text's leading
        // token "Nubank". Teaching "mensagens" instead would outrank every other issuer that also
        // arrives through the SMS app.
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10)),
        )
        val notification = invoicePaymentNotification("pend-inv", app = "Mensagens")
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))
        coEvery { notificationRepository.classify("pend-inv", any()) } returns
            Result.success(ClassifiedNotification(notification, pendingTransactionId = null))
        coEvery { transactionRepository.getMonth(any()) } returns
            Result.success(monthItems + invoiceRow("inv-1", "-1.00", "Fatura Nubank", cardId = "card-1"))
        coEvery { transactionRepository.markPaid("inv-1") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.single())
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(mapOf("nubank" to "card-1"), issuerCardRepository.getMap())
    }

    @Test
    fun `teaching the issuer from the picker resolves the next push from that issuer without the picker`() = runTest {
        // Two cards both literally named "Nubank": the app names a real issuer, but not decisively
        // between them — that ambiguity is exactly what the manual pick resolves and teaches.
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(
                CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10),
                CreditCard("card-2", "Nubank", "Mastercard", "1111", 0L, 0L, BigDecimal("1000"), 15),
            ),
        )
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank", cardId = "card-1"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior", cardId = "card-2"),
        )
        coEvery { transactionRepository.markPaid("inv-2") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first { it.id == "inv-2" })
        testDispatcher.scheduler.advanceUntilIdle()

        // A second, unrelated push from the same issuer: two fresh same-amount pending invoices,
        // one on each card — ambiguous by amount alone, exactly like the first round.
        val secondNotification = invoicePaymentNotification("pend-inv-2")
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(secondNotification))
        coEvery { notificationRepository.classify("pend-inv-2", any()) } returns
            Result.success(ClassifiedNotification(secondNotification, pendingTransactionId = null))
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(
            monthItems +
                invoiceRow("inv-3", "-8866.19", "Fatura Nubank", cardId = "card-1") +
                invoiceRow("inv-4", "-8866.19", "Fatura Nubank anterior", cardId = "card-2"),
        )
        vm.refresh()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals("inv-4", vm.state.value.confirmReady.single().pendingTransactionId)
    }

    @Test
    fun `a failed pick keeps the sheet open with an inline error`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        coEvery { transactionRepository.markPaid(any()) } returns Result.failure(RuntimeException("offline"))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first())
        testDispatcher.scheduler.advanceUntilIdle()

        val picker = vm.state.value.invoicePicker
        assertEquals("Não foi possível marcar como paga. Tente de novo.", picker?.errorMessage)
        assertFalse(picker?.isConfirming ?: true)
        assertEquals(1, vm.state.value.invoicePrompts.size)
    }

    @Test
    fun `a pick that fails after the sheet was already dismissed toasts instead of leaving an invisible error`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        val latch = CompletableDeferred<Result<Unit>>()
        coEvery { transactionRepository.markPaid(any()) } coAnswers { latch.await() }
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first())
        testDispatcher.scheduler.runCurrent()
        // ModalBottomSheet has already animated to Hidden and fired onDismissRequest by the time a
        // real device gets here — the sheet is gone even though isConfirming is still true.
        vm.dismissInvoicePicker()
        latch.complete(Result.failure(RuntimeException("offline")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.invoicePicker)
        assertEquals("Não foi possível marcar como paga. Tente de novo.", vm.state.value.toastMessage)
    }

    @Test
    fun `a successful pick does not close a different prompt's sheet opened while it was in flight`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        val latch = CompletableDeferred<Result<Unit>>()
        coEvery { transactionRepository.markPaid(any()) } coAnswers { latch.await() }
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first())
        testDispatcher.scheduler.runCurrent()
        vm.dismissInvoicePicker()
        val otherPrompt = InvoicePaymentPrompt(notification = recognizedNotification("other"), candidates = emptyList())
        vm.openInvoicePicker(otherPrompt)

        latch.complete(Result.success(Unit))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("other", vm.state.value.invoicePicker?.prompt?.notificationId)
    }

    @Test
    fun `a failed pick does not write its error into a different prompt's sheet opened while it was in flight`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        val latch = CompletableDeferred<Result<Unit>>()
        coEvery { transactionRepository.markPaid(any()) } coAnswers { latch.await() }
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val prompt = vm.state.value.invoicePrompts.single()

        vm.openInvoicePicker(prompt)
        vm.confirmInvoicePayment(prompt.candidates.first())
        testDispatcher.scheduler.runCurrent()
        vm.dismissInvoicePicker()
        val otherPrompt = InvoicePaymentPrompt(notification = recognizedNotification("other"), candidates = emptyList())
        vm.openInvoicePicker(otherPrompt)

        latch.complete(Result.failure(RuntimeException("offline")))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("other", vm.state.value.invoicePicker?.prompt?.notificationId)
        assertNull(vm.state.value.invoicePicker?.errorMessage)
    }

    @Test
    fun `no pending invoice and no matching card falls through to the revisar banner, not a prompt`() = runTest {
        // Regression for the "fatura" false positive: with no card on file for the issuer and no
        // cent-exact invoice, this push is not treated as a credit-card invoice payment at all.
        stubInvoicePush()
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertEquals(1, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `an AUTO invoice push with an echoed EXPENSE type, no pending invoice and no card creates nothing`() = runTest {
        // The matcher declining (no cent-exact invoice, no resolvable card) must not fall through
        // to the ordinary confirm-ready path: the backend's own suggestion for this text is an
        // expense, and one-tap-confirming it would duplicate the invoice the user already tracks.
        val notification = invoicePaymentNotification(
            "pend-inv",
            status = NotificationStatus.AUTO,
            parsedType = TransactionType.EXPENSE,
        )
        coEvery { notificationRepository.getPendingReview() } returns Result.success(listOf(notification))
        coEvery { notificationRepository.classify("pend-inv", any()) } returns
            Result.success(ClassifiedNotification(notification, pendingTransactionId = null))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals(1, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `an invoice push with a known card and no pending invoice still prompts, with an empty candidate list`() = runTest {
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10)),
        )
        stubInvoicePush()
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val prompt = vm.state.value.invoicePrompts.single()
        // expensePending is a plain pending expense, not an invoice: it must not be offered here.
        assertTrue(prompt.candidates.isEmpty())
        assertTrue(vm.state.value.confirmReady.isEmpty())
    }

    @Test
    fun `loadLookups throwing still completes cardsReady so an invoice classify pass does not hang forever`() = runTest {
        // tokenStore.getUserName() is a plain suspend fun, not Result-wrapped like the repository
        // calls in this block, so it is the one call here that can legitimately throw.
        coEvery { tokenStore.getUserName() } throws RuntimeException("datastore corrupted")
        stubInvoicePush()
        val vm = makeViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.classifying)
    }

    @Test
    fun `an invoice classify pass does not hang forever when cardsReady never completes`() = runTest {
        // Never resolves — simulates loadLookups stalling (e.g. a slow tagRepository call) so
        // cardsReady.await() alone would park the classify pass indefinitely.
        val stall = CompletableDeferred<Result<List<Tag>>>()
        coEvery { tagRepository.getAllTags() } coAnswers { stall.await() }
        stubInvoicePush()
        val vm = makeViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.classifying)
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertTrue(vm.state.value.confirmReady.isEmpty())
        assertEquals(1, vm.state.value.pendingReviewCount)
    }

    @Test
    fun `dismissing an invoice prompt marks it ignored and drops only that card`() = runTest {
        coEvery { cardRepository.getCards() } returns Result.success(
            listOf(CreditCard("card-1", "Nubank", "Mastercard", "0000", 0L, 0L, BigDecimal("1000"), 10)),
        )
        stubInvoicePush(invoiceRow("inv-1", "-1.00", "Fatura Nubank"))
        coEvery { notificationRepository.markIgnored("pend-inv") } returns Result.success(Unit)
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.dismissInvoicePrompt(vm.state.value.invoicePrompts.single())
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationRepository.markIgnored("pend-inv") }
        assertTrue(vm.state.value.invoicePrompts.isEmpty())
        assertEquals("Notificação ignorada", vm.state.value.toastMessage)
    }

    @Test
    fun `classify pass is the sole writer of the pending count on the current month`() = runTest {
        // p1 recognized -> card; p2 not recognized (default classify failure) -> stays in the banner.
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(recognizedNotification("p1"), recognizedNotification("p2")))
        coEvery { notificationRepository.classify("p1", any()) } returns
            Result.success(ClassifiedNotification(recognizedNotification("p1"), pendingTransactionId = null))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.confirmReady.size)
        assertEquals("p1", vm.state.value.confirmReady.single().notificationId)
        // Count is set by the classify pass, not reloadMonth's fold: 2 pending − 1 recognized = 1.
        assertEquals(1, vm.state.value.pendingReviewCount)
        assertFalse(vm.state.value.classifying)
    }

    @Test
    fun `classifying and openBillsLoading settle to false after a month flip`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.classifying)
        assertFalse(vm.state.value.openBillsLoading)
    }

    @Test
    fun `a month flip while the invoice picker is open closes the sheet`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openInvoicePicker(vm.state.value.invoicePrompts.single())
        assertTrue(vm.state.value.invoicePicker != null)

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        // Otherwise the sheet floats over another month's Home with a prompt no longer in state.
        assertNull(vm.state.value.invoicePicker)
    }

    @Test
    fun `a same-month reload while the invoice picker is open refreshes its candidate list, so a row settled meanwhile cannot be picked again`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
            invoiceRow("inv-3", "-8866.19", "Fatura Nubank mais antiga"),
        )
        val refresh = LedgerRefreshSignal()
        val vm = makeViewModel(ledgerRefresh = refresh)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openInvoicePicker(vm.state.value.invoicePrompts.single())
        assertEquals(3, vm.state.value.invoicePicker?.prompt?.candidates?.size)

        // inv-2 gets marked paid from Transações meanwhile; the shared ledgerRefresh signal reloads
        // this screen too, and its classify pass must not leave the open sheet offering a stale row.
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(
            monthItems +
                invoiceRow("inv-1", "-8866.19", "Fatura Nubank") +
                invoiceRow("inv-3", "-8866.19", "Fatura Nubank mais antiga"),
        )
        refresh.signal()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("inv-1", "inv-3"), vm.state.value.invoicePicker?.prompt?.candidates?.map { it.id })
    }

    @Test
    fun `a same-month reload while the invoice picker is open closes it once the underlying prompt is fully resolved`() = runTest {
        stubInvoicePush(
            invoiceRow("inv-1", "-8866.19", "Fatura Nubank"),
            invoiceRow("inv-2", "-8866.19", "Fatura Nubank anterior"),
        )
        val refresh = LedgerRefreshSignal()
        val vm = makeViewModel(ledgerRefresh = refresh)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.openInvoicePicker(vm.state.value.invoicePrompts.single())
        assertTrue(vm.state.value.invoicePicker != null)

        // inv-2 gets marked paid from Transações meanwhile: only inv-1 remains, so amount alone now
        // resolves the match — this notification leaves invoicePrompts entirely.
        coEvery { transactionRepository.getMonth(any()) } returns
            Result.success(monthItems + invoiceRow("inv-1", "-8866.19", "Fatura Nubank"))
        refresh.signal()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(vm.state.value.invoicePicker)
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
    fun `onManualRefresh flushes the tag context lookup caches before reloading`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onManualRefresh()
        testDispatcher.scheduler.advanceUntilIdle()

        // Without this the reload just re-serves the stale in-memory lookups, so a category/tag added
        // on the web would never appear on a pull-to-refresh.
        verify { tagRepository.refreshLookups() }
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
