package com.resolveprogramming.pocketcounter.ui.home

import com.resolveprogramming.pocketcounter.data.local.TokenStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.NotificationRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.AutomationStat
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
import com.resolveprogramming.pocketcounter.ui.transacoes.FormMode
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
        coEvery { cardRepository.getOpenInvoices() } returns Result.success(emptyList())
        coEvery { tokenStore.getUserName() } returns "Guilherme"
        coEvery { notificationRepository.getPendingReview() } returns Result.success(emptyList())
        coEvery { notificationRepository.getAutomationStat() } returns
            Result.success(AutomationStat(monthTotal = 10, autoDone = 7))
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(monthItems)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(): HomeViewModel = HomeViewModel(
        notificationRepository = notificationRepository,
        transactionRepository = transactionRepository,
        tagRepository = tagRepository,
        cardRepository = cardRepository,
        tokenStore = tokenStore,
    )

    @Test
    fun `init loads current month KPIs and automation`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        // expensePaid + expensePending are both expenses; income is the only INCOME row.
        assertEquals(2, s.kpis.expenseCount)
        assertEquals(1, s.kpis.incomeCount)
        assertEquals(BigDecimal("40.00"), s.kpis.pendingTotal)
        assertEquals(1, s.kpis.pendingCount)
        assertEquals(70, s.automationPct)
        assertTrue(s.isCurrentMonth)
    }

    @Test
    fun `selectMonth off current month gates banner and automation`() = runTest {
        coEvery { notificationRepository.getPendingReview() } returns
            Result.success(listOf(mockk(relaxed = true), mockk(relaxed = true)))
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.selectMonth(-1)
        testDispatcher.scheduler.advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.isCurrentMonth)
        assertNull(s.automationPct)
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
        assertEquals(FormMode.Add, vm.state.value.formMode)

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
}
