package com.resolveprogramming.pocketcounter.ui.transacoes

import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Series
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests for the LedgerFilter feature and toggleFixo action on TransacoesViewModel.
 *
 * === Assumed VM signatures the implementer must provide ===
 *
 * Enum (top-level or nested in the ui/transacoes package):
 *   enum class LedgerFilter { TODOS, FIXOS }
 *
 * TransacoesUiState new fields:
 *   val ledgerFilter: LedgerFilter = LedgerFilter.TODOS
 *   val fixoCount: Int = 0
 *
 * HistoryItem new fields (trailing, defaulted):
 *   val seriesId: String? = null
 *   val isFixo: Boolean get() = seriesId != null
 *
 * TransacoesViewModel new actions:
 *   fun setLedgerFilter(filter: LedgerFilter)
 *   fun toggleFixo(item: HistoryItem)
 *
 * Displayed list asserted against: state.dayGroups
 *   (default LISTA GroupMode; items are flattened from DayGroup.items across all day buckets)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransacoesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val transactionRepository: TransactionRepository = mockk()
    private val cardRepository: CardRepository = mockk()
    private val tagRepository: TagRepository = mockk()
    private val seriesRepository: SeriesRepository = mockk()

    // A fixed month key that avoids coupling to real clock
    private val monthKey = "2026-06"

    // Two avulso items (seriesId == null) and two fixo items (seriesId != null)
    private val avulsoItem1 = HistoryItem(
        id = "avulso-1",
        date = LocalDate.of(2026, 6, 4),
        amount = BigDecimal("50.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        seriesId = null,
    )
    private val avulsoItem2 = HistoryItem(
        id = "avulso-2",
        date = LocalDate.of(2026, 6, 10),
        amount = BigDecimal("30.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        seriesId = null,
    )
    private val fixoItem1 = HistoryItem(
        id = "fixo-1",
        date = LocalDate.of(2026, 6, 5),
        amount = BigDecimal("200.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        seriesId = "s1",
    )
    private val fixoItem2 = HistoryItem(
        id = "fixo-2",
        date = LocalDate.of(2026, 6, 15),
        amount = BigDecimal("100.00"),
        type = TransactionType.INCOME,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        seriesId = "s2",
    )

    private val allItems = listOf(avulsoItem1, avulsoItem2, fixoItem1, fixoItem2)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        // Default stubs for loadLookups()
        coEvery { cardRepository.getCards() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())

        // Default stub for loadMonth() — the VM calls getMonth with its current monthKey
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(allItems)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(): TransacoesViewModel = TransacoesViewModel(
        transactionRepository = transactionRepository,
        cardRepository = cardRepository,
        tagRepository = tagRepository,
        seriesRepository = seriesRepository,
    )

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Flattens all DayGroup.items into a single list. */
    private fun flatDisplayedItems(state: TransacoesUiState): List<HistoryItem> =
        state.dayGroups.flatMap { it.items }

    // -------------------------------------------------------------------------
    // setLedgerFilter: FIXOS shows only series-linked rows; TODOS shows all
    // -------------------------------------------------------------------------

    @Test
    fun `setLedgerFilter FIXOS shows only series-linked rows`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        val displayed = flatDisplayedItems(vm.state.value)
        assertEquals(2, displayed.size)
        assert(displayed.all { it.isFixo }) { "Expected only fixo rows but got $displayed" }
    }

    @Test
    fun `setLedgerFilter TODOS shows all rows after being in FIXOS`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)
        vm.setLedgerFilter(LedgerFilter.TODOS)

        val displayed = flatDisplayedItems(vm.state.value)
        assertEquals(4, displayed.size)
    }

    // -------------------------------------------------------------------------
    // fixoCount: counts fixo rows from the full month list, ignores active filter
    // -------------------------------------------------------------------------

    @Test
    fun `fixoCount counts fixo rows from the full month regardless of filter`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // fixoCount must be 2 before any filter change
        assertEquals(2, vm.state.value.fixoCount)

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        // fixoCount must remain 2 even while FIXOS filter is active
        assertEquals(2, vm.state.value.fixoCount)
    }

    // -------------------------------------------------------------------------
    // Totals are NOT filtered — computed from the full month list
    // -------------------------------------------------------------------------

    @Test
    fun `totals are unaffected by the FIXOS filter`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val totalsBeforeFilter = vm.state.value.totals

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        assertEquals(totalsBeforeFilter, vm.state.value.totals)
    }

    @Test
    fun `totals income equals sum of INCOME items across full list`() = runTest {
        // fixoItem2 is the only INCOME item, amount = 100.00
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val totals = vm.state.value.totals

        assertEquals(BigDecimal("100.00"), totals.income)
    }

    @Test
    fun `totals expense equals sum of EXPENSE item amounts across full list`() = runTest {
        // avulsoItem1=50, avulsoItem2=30, fixoItem1=200
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)
        val totals = vm.state.value.totals

        assertEquals(BigDecimal("280.00"), totals.expense)
    }

    // -------------------------------------------------------------------------
    // toggleFixo on avulso row: create series then link transaction, then reload
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFixo on avulso row creates a series then links the transaction`() = runTest {
        val createdSeries = Series("s-new", "src-1", TransactionType.EXPENSE, avulsoItem1.date.dayOfMonth)
        coEvery { seriesRepository.create(any(), eq(avulsoItem1.type), any()) } returns Result.success(createdSeries)
        coEvery { seriesRepository.linkTransaction("s-new", avulsoItem1.id, false) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val callCount = mutableListOf<String>()

        vm.toggleFixo(avulsoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerifyOrder {
            seriesRepository.create(any(), eq(avulsoItem1.type), any())
            seriesRepository.linkTransaction("s-new", avulsoItem1.id, false)
        }
    }

    @Test
    fun `toggleFixo on avulso row reloads month after linking`() = runTest {
        val createdSeries = Series("s-new", "src-1", TransactionType.EXPENSE, avulsoItem1.date.dayOfMonth)
        coEvery { seriesRepository.create(any(), any(), any()) } returns Result.success(createdSeries)
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(avulsoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        // getMonth should have been called at least twice: once on init, once after toggleFixo
        coVerify(atLeast = 2) { transactionRepository.getMonth(any()) }
    }

    @Test
    fun `toggleFixo on avulso row uses item type when creating series`() = runTest {
        val createdSeries = Series("s-new", "src-1", TransactionType.EXPENSE, avulsoItem1.date.dayOfMonth)
        coEvery { seriesRepository.create(any(), eq(TransactionType.EXPENSE), any()) } returns Result.success(createdSeries)
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(avulsoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.create(any(), eq(TransactionType.EXPENSE), any()) }
    }

    @Test
    fun `toggleFixo on avulso row uses item dayOfMonth as recurrenceDay`() = runTest {
        // avulsoItem1.date = 2026-06-04, so recurrenceDay should be 4
        val createdSeries = Series("s-new", "src-1", TransactionType.EXPENSE, 4)
        coEvery { seriesRepository.create(any(), any(), eq(4)) } returns Result.success(createdSeries)
        coEvery { seriesRepository.linkTransaction(any(), any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(avulsoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.create(any(), any(), eq(4)) }
    }

    // -------------------------------------------------------------------------
    // toggleFixo on fixo row: unlinks, does NOT create/link, then reloads
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFixo on fixo row unlinks the transaction`() = runTest {
        coEvery { seriesRepository.unlinkTransaction("s1", fixoItem1.id) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(fixoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { seriesRepository.unlinkTransaction("s1", fixoItem1.id) }
    }

    @Test
    fun `toggleFixo on fixo row does not call create or linkTransaction`() = runTest {
        coEvery { seriesRepository.unlinkTransaction(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(fixoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { seriesRepository.create(any(), any(), any()) }
        coVerify(exactly = 0) { seriesRepository.linkTransaction(any(), any(), any()) }
    }

    @Test
    fun `toggleFixo on fixo row reloads month after unlinking`() = runTest {
        coEvery { seriesRepository.unlinkTransaction(any(), any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(fixoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(atLeast = 2) { transactionRepository.getMonth(any()) }
    }

    // -------------------------------------------------------------------------
    // toggleFixo create failure: best-effort — no crash, toast surfaced, no link call
    // -------------------------------------------------------------------------

    @Test
    fun `toggleFixo create failure does not crash and surfaces a toast`() = runTest {
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.failure(RuntimeException("series create failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(avulsoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.state.value.toastMessage)
    }

    @Test
    fun `toggleFixo create failure does not call linkTransaction`() = runTest {
        coEvery { seriesRepository.create(any(), any(), any()) } returns
            Result.failure(RuntimeException("series create failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleFixo(avulsoItem1)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { seriesRepository.linkTransaction(any(), any(), any()) }
    }

    // -------------------------------------------------------------------------
    // LedgerFilter state field reflects what was set
    // -------------------------------------------------------------------------

    @Test
    fun `initial ledgerFilter is TODOS`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LedgerFilter.TODOS, vm.state.value.ledgerFilter)
    }

    @Test
    fun `setLedgerFilter updates ledgerFilter in state`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        assertEquals(LedgerFilter.FIXOS, vm.state.value.ledgerFilter)
    }

    // -------------------------------------------------------------------------
    // FIXOS filter composes with search (query) filter
    // -------------------------------------------------------------------------

    @Test
    fun `FIXOS filter composes with search query — only fixo rows matching query appear`() = runTest {
        // Only fixoItem1 is EXPENSE type; fixoItem2 is INCOME.
        // Stub sources/paymentSources as empty so the search text falls back to amount matching.
        // Query something only fixoItem1's amount matches: "200"
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)
        vm.toggleSearch()
        vm.setQuery("200")

        val displayed = flatDisplayedItems(vm.state.value)
        // Only fixoItem1 matches "200" AND isFixo
        assertEquals(1, displayed.size)
        assertEquals("fixo-1", displayed.first().id)
    }
}
