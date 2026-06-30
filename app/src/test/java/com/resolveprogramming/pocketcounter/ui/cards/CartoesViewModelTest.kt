package com.resolveprogramming.pocketcounter.ui.cards

import com.resolveprogramming.pocketcounter.data.local.ViewedMonthStore
import com.resolveprogramming.pocketcounter.data.repository.CardRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.InvoiceItem
import com.resolveprogramming.pocketcounter.domain.model.OpenInvoice
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.UNCATEGORIZED_CONTEXT_ID
import io.mockk.coEvery
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class CartoesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val cardRepository: CardRepository = mockk()
    private val tagRepository: TagRepository = mockk()

    // ── fixture data ──────────────────────────────────────────────────────────

    private val card1 = CreditCard(
        id = "card-1",
        name = "Nubank",
        brand = "Mastercard",
        last4 = "1234",
        gradientStart = 0xFF820AD1L,
        gradientEnd = 0xFF4A0A8AL,
        limit = BigDecimal("5000.00"),
        billDay = 10,
    )

    private val card2 = CreditCard(
        id = "card-2",
        name = "Itaú",
        brand = "Visa",
        last4 = "5678",
        gradientStart = 0xFFFF6900L,
        gradientEnd = 0xFFCC4400L,
        limit = BigDecimal("3000.00"),
        billDay = 15,
    )

    private val contextFood = TagContext(id = "ctx-food", name = "Alimentação", color = 0xFF43A047L)
    private val contextTransport = TagContext(id = "ctx-transport", name = "Transporte", color = 0xFF1E88E5L)

    // Tags as they appear in allTags (the catalog); idContext is the authoritative mapping
    private val tagBurger = Tag(
        id = "tag-burger",
        name = "Restaurante",
        kind = TransactionType.EXPENSE,
        idContext = "ctx-food",
    )
    private val tagUber = Tag(
        id = "tag-uber",
        name = "Uber",
        kind = TransactionType.EXPENSE,
        idContext = "ctx-transport",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Defaults; individual tests override as needed.
        coEvery { cardRepository.getOpenInvoices(any()) } returns Result.success(emptyList())
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(viewedMonth: ViewedMonthStore = ViewedMonthStore()): CartoesViewModel =
        CartoesViewModel(
            cardRepository = cardRepository,
            tagRepository = tagRepository,
            viewedMonth = viewedMonth,
        )

    private fun invoiceItem(
        id: String = "item-1",
        amount: BigDecimal,
        tags: List<Tag> = emptyList(),
    ) = InvoiceItem(
        transactionId = "tx-$id",
        invoiceId = "inv-1",
        itemId = id,
        name = id,
        date = LocalDate.of(2026, 6, 4),
        amount = amount,
        tags = tags,
        installmentLabel = null,
    )

    private fun openInvoice(
        card: CreditCard,
        total: BigDecimal,
        items: List<InvoiceItem>,
    ) = OpenInvoice(
        card = card,
        total = total,
        usage = 0.2f,
        closesInDays = 10,
        dueLabel = "15 jul",
        items = items,
    )

    // ── categoriesByCardId ────────────────────────────────────────────────────

    @Test
    fun `after load categoriesByCardId groups sum to invoice total`() = runTest {
        // invoice total 100, one item 70 tagged → remainder 30 folded into Sem categoria
        val invoice = openInvoice(
            card = card1,
            total = BigDecimal("100.00"),
            items = listOf(
                invoiceItem("i1", BigDecimal("70.00"), tags = listOf(tagBurger)),
            ),
        )
        coEvery { cardRepository.getOpenInvoices(any()) } returns Result.success(listOf(invoice))
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(tagBurger))
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(contextFood))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val groups = vm.state.value.categoriesByCardId["card-1"]
        assertNotNull("Expected groups for card-1", groups)
        val groupsTotal = groups!!.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
        assertEquals(0, groupsTotal.compareTo(BigDecimal("100.00")))
    }

    @Test
    fun `after load item with known context is bucketed under that context name`() = runTest {
        val invoice = openInvoice(
            card = card1,
            total = BigDecimal("50.00"),
            items = listOf(
                invoiceItem("i1", BigDecimal("50.00"), tags = listOf(tagBurger)),
            ),
        )
        coEvery { cardRepository.getOpenInvoices(any()) } returns Result.success(listOf(invoice))
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(tagBurger))
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(contextFood))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val groups = vm.state.value.categoriesByCardId["card-1"]
        assertNotNull(groups)
        val foodGroup = groups!!.firstOrNull { it.id == "ctx-food" }
        assertNotNull("Expected a group for ctx-food (Alimentação)", foodGroup)
        assertEquals("Alimentação", foodGroup!!.name)
        assertEquals(0, foodGroup.total.compareTo(BigDecimal("50.00")))
    }

    @Test
    fun `after load untagged item lands in sem categoria`() = runTest {
        val invoice = openInvoice(
            card = card1,
            total = BigDecimal("35.00"),
            items = listOf(
                invoiceItem("i1", BigDecimal("35.00"), tags = emptyList()),
            ),
        )
        coEvery { cardRepository.getOpenInvoices(any()) } returns Result.success(listOf(invoice))
        coEvery { tagRepository.getAllTags() } returns Result.success(emptyList())
        coEvery { tagRepository.getAllContexts() } returns Result.success(emptyList())

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val groups = vm.state.value.categoriesByCardId["card-1"]
        assertNotNull(groups)
        assertTrue(
            "Untagged item must produce a '$UNCATEGORIZED_CONTEXT_ID' group",
            groups!!.any { it.id == UNCATEGORIZED_CONTEXT_ID },
        )
    }

    @Test
    fun `two cards produce independent per-card breakdowns`() = runTest {
        // card-1: total=100, one item 60 in food
        // card-2: total=80, one item 80 in transport
        val invoice1 = openInvoice(
            card = card1,
            total = BigDecimal("100.00"),
            items = listOf(invoiceItem("i1", BigDecimal("60.00"), tags = listOf(tagBurger))),
        )
        val invoice2 = openInvoice(
            card = card2,
            total = BigDecimal("80.00"),
            items = listOf(invoiceItem("i2", BigDecimal("80.00"), tags = listOf(tagUber))),
        )
        coEvery { cardRepository.getOpenInvoices(any()) } returns Result.success(listOf(invoice1, invoice2))
        coEvery { tagRepository.getAllTags() } returns Result.success(listOf(tagBurger, tagUber))
        coEvery { tagRepository.getAllContexts() } returns Result.success(listOf(contextFood, contextTransport))

        val vm = makeViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val byId = vm.state.value.categoriesByCardId
        assertNotNull("card-1 must have a breakdown", byId["card-1"])
        assertNotNull("card-2 must have a breakdown", byId["card-2"])

        // card-1: food=60 + sem categoria=40 (remainder)
        val total1 = byId["card-1"]!!.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
        assertEquals(0, total1.compareTo(BigDecimal("100.00")))
        assertTrue(byId["card-1"]!!.any { it.id == "ctx-food" })

        // card-2: transport=80, fully itemized
        val total2 = byId["card-2"]!!.fold(BigDecimal.ZERO) { acc, g -> acc + g.total }
        assertEquals(0, total2.compareTo(BigDecimal("80.00")))
        assertTrue(byId["card-2"]!!.any { it.id == "ctx-transport" })

        // card-1's breakdown does not bleed into card-2's
        assertTrue(byId["card-2"]!!.none { it.id == "ctx-food" })
    }
}
