package com.resolveprogramming.pocketcounter.ui.transacoes

import app.cash.turbine.test
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.SeriesRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.TransactionRepository
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.Series
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.WizardDraft
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
 * Displayed list asserted against: state.listItems
 *   (default LISTA GroupMode; a flat list in the backend's order — no day grouping)
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
        viewedMonth = ViewedMonthStore(),
    )

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** The flat displayed list (LISTA mode). */
    private fun flatDisplayedItems(state: TransacoesUiState): List<HistoryItem> =
        state.listItems

    // -------------------------------------------------------------------------
    // setLedgerFilter: FIXOS shows only series-linked rows; TODOS shows all
    // -------------------------------------------------------------------------

    @Test
    fun `setLedgerFilter FIXOS shows only series-linked rows within active type`() = runTest {
        // Default typeFilter=EXPENSE. EXPENSE fixos: fixoItem1 only (fixoItem2 is INCOME).
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        val displayed = flatDisplayedItems(vm.state.value)
        assertEquals(1, displayed.size)
        assert(displayed.all { it.isFixo }) { "Expected only fixo rows but got $displayed" }
    }

    @Test
    fun `setLedgerFilter TODOS shows all active-type rows after being in FIXOS`() = runTest {
        // Default typeFilter=EXPENSE: 3 EXPENSE rows (avulso1, avulso2, fixo1).
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setLedgerFilter(LedgerFilter.FIXOS)
        vm.setLedgerFilter(LedgerFilter.TODOS)

        val displayed = flatDisplayedItems(vm.state.value)
        assertEquals(3, displayed.size)
    }

    // -------------------------------------------------------------------------
    // fixoCount: counts fixo rows within the active type, ignores ledger filter
    // -------------------------------------------------------------------------

    @Test
    fun `fixoCount counts fixo rows within active type regardless of ledger filter`() = runTest {
        // Default typeFilter=EXPENSE. EXPENSE fixos: fixoItem1 only → fixoCount=1.
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.fixoCount)

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        // fixoCount must remain 1 (within EXPENSE) even while FIXOS filter is active
        assertEquals(1, vm.state.value.fixoCount)
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

    // -------------------------------------------------------------------------
    // Order is the backend's (ORDER BY displayOrder) — the client never re-sorts, and a status
    // toggle must not reorder rows. (Mirrors the web client, which renders the response as-is.)
    // -------------------------------------------------------------------------

    @Test
    fun `markPaid keeps the backend order and just flips status`() = runTest {
        val tiedA = HistoryItem(
            id = "tied-a", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("10.00"),
            type = TransactionType.EXPENSE, tagIds = null, statusPayment = PaymentStatus.PENDING,
            displayOrder = 0,
        )
        val tiedB = HistoryItem(
            id = "tied-b", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("20.00"),
            type = TransactionType.EXPENSE, tagIds = null, statusPayment = PaymentStatus.PENDING,
            displayOrder = 0,
        )
        // Backend returns its stable order [B, A] on every load (it ORDERs BY displayOrder); the client
        // must show exactly that, not re-sort to [A, B]. The post-toggle reload keeps that order.
        coEvery { transactionRepository.getMonth(any()) } returnsMany listOf(
            Result.success(listOf(tiedB, tiedA)),
            Result.success(listOf(tiedB, tiedA.copy(statusPayment = PaymentStatus.PAID))),
        )
        coEvery { transactionRepository.markPaid("tied-a") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val beforeIds = flatDisplayedItems(vm.state.value).map { it.id }
        assertEquals(listOf("tied-b", "tied-a"), beforeIds)

        vm.markPaid("tied-a")
        testDispatcher.scheduler.advanceUntilIdle()

        val afterItems = flatDisplayedItems(vm.state.value)
        assertEquals(beforeIds, afterItems.map { it.id })
        assertEquals(PaymentStatus.PAID, afterItems.first { it.id == "tied-a" }.statusPayment)
    }

    @Test
    fun `markPending keeps the backend order and just flips status`() = runTest {
        val tiedA = HistoryItem(
            id = "tied-a", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("10.00"),
            type = TransactionType.EXPENSE, tagIds = null, statusPayment = PaymentStatus.PAID,
            displayOrder = 0,
        )
        val tiedB = HistoryItem(
            id = "tied-b", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("20.00"),
            type = TransactionType.EXPENSE, tagIds = null, statusPayment = PaymentStatus.PAID,
            displayOrder = 0,
        )
        // Backend returns its stable order [B, A] on every load; the client must preserve it.
        coEvery { transactionRepository.getMonth(any()) } returnsMany listOf(
            Result.success(listOf(tiedB, tiedA)),
            Result.success(listOf(tiedB.copy(statusPayment = PaymentStatus.PENDING), tiedA)),
        )
        coEvery { transactionRepository.markPending("tied-b") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val beforeIds = flatDisplayedItems(vm.state.value).map { it.id }
        assertEquals(listOf("tied-b", "tied-a"), beforeIds)

        vm.markPending("tied-b")
        testDispatcher.scheduler.advanceUntilIdle()

        val afterItems = flatDisplayedItems(vm.state.value)
        assertEquals(beforeIds, afterItems.map { it.id })
        assertEquals(PaymentStatus.PENDING, afterItems.first { it.id == "tied-b" }.statusPayment)
    }

    @Test
    fun `the list preserves the backend order and never re-sorts`() = runTest {
        // Same day, distinct displayOrder. The backend already returns rows in displayOrder order; the
        // client must render that order verbatim — here [C, A, B] — not re-sort by date/amount/id.
        val itemA = HistoryItem(
            id = "ord-a", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("10.00"),
            type = TransactionType.EXPENSE, tagIds = null, displayOrder = 1,
        )
        val itemB = HistoryItem(
            id = "ord-b", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("20.00"),
            type = TransactionType.EXPENSE, tagIds = null, displayOrder = 2,
        )
        val itemC = HistoryItem(
            id = "ord-c", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("30.00"),
            type = TransactionType.EXPENSE, tagIds = null, displayOrder = 0,
        )
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(listOf(itemC, itemA, itemB))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val displayedIds = flatDisplayedItems(vm.state.value).map { it.id }

        assertEquals(listOf("ord-c", "ord-a", "ord-b"), displayedIds)
    }

    // -------------------------------------------------------------------------
    // Optimistic status toggle: flips instantly, no full-screen loading
    // -------------------------------------------------------------------------

    @Test
    fun `markPending optimistically flips status without entering loading state`() = runTest {
        // avulsoItem1 starts PAID; markPending should flip it to PENDING synchronously.
        coEvery { transactionRepository.markPending("avulso-1") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.markPending("avulso-1")

        // Before any coroutine runs: the row flipped and the blocking spinner did not appear.
        val optimistic = vm.state.value.items.first { it.id == "avulso-1" }
        assertEquals(PaymentStatus.PENDING, optimistic.statusPayment)
        assertFalse("Status toggle must not show full-screen loading", vm.state.value.isLoading)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `markPaid failure reverts the optimistic status and surfaces an error`() = runTest {
        val pending = avulsoItem1.copy(statusPayment = PaymentStatus.PENDING)
        coEvery { transactionRepository.getMonth(any()) } returns
            Result.success(listOf(pending, avulsoItem2, fixoItem1, fixoItem2))
        coEvery { transactionRepository.markPaid("avulso-1") } returns
            Result.failure(RuntimeException("status update failed"))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.markPaid("avulso-1")
        testDispatcher.scheduler.advanceUntilIdle()

        val reverted = vm.state.value.items.first { it.id == "avulso-1" }
        assertEquals(PaymentStatus.PENDING, reverted.statusPayment)
        assertNotNull(vm.state.value.toastMessage)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `optimistic status toggle preserves backend order and the flat list`() = runTest {
        val ordA = HistoryItem(
            id = "ord-a", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("10.00"),
            type = TransactionType.EXPENSE, tagIds = null,
            statusPayment = PaymentStatus.PENDING, displayOrder = 0,
        )
        val ordB = HistoryItem(
            id = "ord-b", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("20.00"),
            type = TransactionType.EXPENSE, tagIds = null,
            statusPayment = PaymentStatus.PENDING, displayOrder = 0,
        )
        val ordC = HistoryItem(
            id = "ord-c", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("30.00"),
            type = TransactionType.EXPENSE, tagIds = null,
            statusPayment = PaymentStatus.PENDING, displayOrder = 0,
        )
        // Backend order is [C, A, B]; the client renders it verbatim, so a status toggle must keep it.
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(listOf(ordC, ordA, ordB))
        coEvery { transactionRepository.markPaid("ord-b") } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val beforeIds = flatDisplayedItems(vm.state.value).map { it.id }

        vm.markPaid("ord-b")

        // Inspect the optimistic state before the backend confirm/reload runs.
        val afterItems = flatDisplayedItems(vm.state.value)
        assertEquals(beforeIds, afterItems.map { it.id })
        assertEquals(3, vm.state.value.listItems.size)
        assertEquals(PaymentStatus.PAID, afterItems.first { it.id == "ord-b" }.statusPayment)
    }

    @Test
    fun `moveItemTo optimistically reflects the new order before any backend reload`() = runTest {
        // Three same-day EXPENSE rows in canonical order [A, B, C] (displayOrder 0,1,2).
        val ordA = HistoryItem(
            id = "ord-a", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("10.00"),
            type = TransactionType.EXPENSE, tagIds = null, displayOrder = 0,
        )
        val ordB = HistoryItem(
            id = "ord-b", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("20.00"),
            type = TransactionType.EXPENSE, tagIds = null, displayOrder = 1,
        )
        val ordC = HistoryItem(
            id = "ord-c", date = LocalDate.of(2026, 6, 4), amount = BigDecimal("30.00"),
            type = TransactionType.EXPENSE, tagIds = null, displayOrder = 2,
        )
        coEvery { transactionRepository.getMonth(any()) } returns Result.success(listOf(ordA, ordB, ordC))
        coEvery { transactionRepository.reorder(any()) } returns Result.success(Unit)

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Grouped mode so there's a LedgerGroup to drag within (all rows share "Sem categoria").
        vm.setGroupMode(GroupMode.CONTEXTO)
        val group = vm.state.value.ledgerGroups.first()
        assertEquals(listOf("ord-a", "ord-b", "ord-c"), group.items.map { it.id })

        // Drag A to the end of its group.
        vm.moveItemTo(group, ordA, targetIndex = 2)
        testDispatcher.scheduler.advanceUntilIdle()

        // The reorder succeeds (no reload reverts it), so the moved list order is visible immediately
        // (recomputed() preserves the new order instead of re-sorting it away).
        val newOrder = vm.state.value.ledgerGroups.first().items.map { it.id }
        assertEquals(listOf("ord-b", "ord-c", "ord-a"), newOrder)
    }

    // =========================================================================
    // typeFilter — active-type filter (A from architect plan)
    // =========================================================================

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial typeFilter is EXPENSE`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(TransactionType.EXPENSE, vm.state.value.typeFilter)
    }

    // -------------------------------------------------------------------------
    // setTypeFilter happy path
    // -------------------------------------------------------------------------

    @Test
    fun `setTypeFilter INCOME shows only INCOME rows in the list`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setTypeFilter(TransactionType.INCOME)

        val displayed = flatDisplayedItems(vm.state.value)
        assertTrue("Expected only INCOME rows", displayed.all { it.type == TransactionType.INCOME })
        assertEquals(1, displayed.size) // fixoItem2 is the only INCOME row
        assertEquals("fixo-2", displayed.first().id)
    }

    @Test
    fun `setTypeFilter EXPENSE returns to EXPENSE rows`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setTypeFilter(TransactionType.INCOME)
        vm.setTypeFilter(TransactionType.EXPENSE)

        val displayed = flatDisplayedItems(vm.state.value)
        assertTrue("Expected only EXPENSE rows", displayed.all { it.type == TransactionType.EXPENSE })
        assertEquals(3, displayed.size) // avulso1, avulso2, fixo1
    }

    // -------------------------------------------------------------------------
    // typeTotal and typeCount
    // -------------------------------------------------------------------------

    @Test
    fun `initial typeTotal is sum of EXPENSE magnitudes and typeCount equals EXPENSE row count`() = runTest {
        // EXPENSE: avulso1(50) + avulso2(30) + fixo1(200) = 280; count = 3
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(BigDecimal("280.00"), vm.state.value.typeTotal)
        assertEquals(3, vm.state.value.typeCount)
    }

    @Test
    fun `setTypeFilter INCOME changes typeTotal and typeCount to INCOME rows`() = runTest {
        // INCOME: fixoItem2(100); count = 1
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setTypeFilter(TransactionType.INCOME)

        assertEquals(BigDecimal("100.00"), vm.state.value.typeTotal)
        assertEquals(1, vm.state.value.typeCount)
    }

    // -------------------------------------------------------------------------
    // expenseCount / incomeCount — stable, full-month, filter-independent
    // -------------------------------------------------------------------------

    @Test
    fun `expenseCount and incomeCount reflect full month and are unaffected by ledger filter`() = runTest {
        // Full month: EXPENSE=3, INCOME=1
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, vm.state.value.expenseCount)
        assertEquals(1, vm.state.value.incomeCount)

        vm.setLedgerFilter(LedgerFilter.FIXOS)

        assertEquals(3, vm.state.value.expenseCount)
        assertEquals(1, vm.state.value.incomeCount)
    }

    @Test
    fun `expenseCount and incomeCount are unaffected by search query`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleSearch()
        vm.setQuery("50") // matches only avulsoItem1

        assertEquals(3, vm.state.value.expenseCount)
        assertEquals(1, vm.state.value.incomeCount)
    }

    // -------------------------------------------------------------------------
    // Type + FIXOS combination
    // -------------------------------------------------------------------------

    @Test
    fun `FIXOS filter within INCOME type shows only INCOME fixo rows`() = runTest {
        // fixoItem2 is INCOME+fixo; fixoItem1 is EXPENSE+fixo — must NOT appear
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setTypeFilter(TransactionType.INCOME)
        vm.setLedgerFilter(LedgerFilter.FIXOS)

        val displayed = flatDisplayedItems(vm.state.value)
        assertEquals(1, displayed.size)
        assertEquals("fixo-2", displayed.first().id)
    }

    @Test
    fun `fixoCount reflects fixos within active type only`() = runTest {
        // EXPENSE: fixo1 → fixoCount=1; INCOME: fixo2 → fixoCount=1
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, vm.state.value.fixoCount)

        vm.setTypeFilter(TransactionType.INCOME)

        assertEquals(1, vm.state.value.fixoCount)
    }

    // -------------------------------------------------------------------------
    // Type + search combination
    // -------------------------------------------------------------------------

    @Test
    fun `search within EXPENSE type excludes INCOME rows from results`() = runTest {
        // fixoItem2 is INCOME with amount=100. Query "100" with EXPENSE filter should show nothing.
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.toggleSearch()
        vm.setQuery("100") // amount matches fixoItem2 (INCOME), but typeFilter=EXPENSE

        val displayed = flatDisplayedItems(vm.state.value)
        assertTrue("Expected no INCOME rows in EXPENSE view", displayed.none { it.type == TransactionType.INCOME })
        assertTrue("Expected no rows matching INCOME-only search term", displayed.isEmpty())
    }

    // -------------------------------------------------------------------------
    // Type + GroupMode combination
    // -------------------------------------------------------------------------

    @Test
    fun `CONTEXTO group mode only groups active-type items`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.setGroupMode(GroupMode.CONTEXTO)

        // With typeFilter=EXPENSE, all ledger group rows must be EXPENSE
        val allGroupedItems = vm.state.value.ledgerGroups.flatMap { it.items }
        assertTrue("Expected EXPENSE-only grouped rows", allGroupedItems.all { it.type == TransactionType.EXPENSE })
    }

    // -------------------------------------------------------------------------
    // Regression: totals are full-month and unaffected by typeFilter
    // -------------------------------------------------------------------------

    @Test
    fun `totals are unaffected by typeFilter change`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val totalsBeforeFilter = vm.state.value.totals

        vm.setTypeFilter(TransactionType.INCOME)

        assertEquals(totalsBeforeFilter, vm.state.value.totals)
    }

    // =========================================================================
    // openAdd with type pre-seed (B from architect plan)
    // =========================================================================

    @Test
    fun `openAdd with type seeds FormMode Add with that type`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openAdd(TransactionType.INCOME)

        val mode = vm.state.value.formMode
        assertEquals(FormMode.Add(TransactionType.INCOME), mode)
    }

    @Test
    fun `openAdd with no type seeds FormMode Add with null initialType`() = runTest {
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openAdd()

        val mode = vm.state.value.formMode as? FormMode.Add
        assertNotNull(mode)
        assertNull(mode!!.initialType)
    }

    // =========================================================================
    // saveForm in Add mode (mirrors HomeViewModelTest pattern)
    // =========================================================================

    @Test
    fun `saveForm in Add mode calls save and clears formMode then reloads`() = runTest {
        coEvery { transactionRepository.save(any()) } returns Result.success("new-id")

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.openAdd()
        vm.saveForm(WizardDraft(type = TransactionType.EXPENSE, amount = BigDecimal("10.00")))
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transactionRepository.save(any()) }
        assertNull(vm.state.value.formMode)
        // getMonth called at least twice: init + after save
        coVerify(atLeast = 2) { transactionRepository.getMonth(any()) }
    }

    @Test
    fun `openAdd refreshes the card list so a newly-added card appears in the form`() = runTest {
        // VM initialised with no cards; a card is added on the backend afterwards.
        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.state.value.cards.isEmpty())

        val card = CreditCard(
            id = "c1", name = "Nubank", brand = "", last4 = "",
            gradientStart = 0L, gradientEnd = 0L, limit = BigDecimal.ZERO, billDay = 10,
        )
        coEvery { cardRepository.getCards() } returns Result.success(listOf(card))

        vm.openAdd()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(card), vm.state.value.cards)
        assertTrue(vm.state.value.formMode is FormMode.Add)
    }
}
