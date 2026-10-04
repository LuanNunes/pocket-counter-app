package com.resolveprogramming.pocketcounter.data.repository

import com.resolveprogramming.pocketcounter.data.remote.api.CreditCardApi
import com.resolveprogramming.pocketcounter.data.remote.api.InvoiceItemApi
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.CreditCardDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionItemDto
import com.resolveprogramming.pocketcounter.domain.billing.BillingCycle
import com.resolveprogramming.pocketcounter.domain.model.ClassificationRule
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class RetrofitCardRepositoryTest {

    private val creditCardApi = mockk<CreditCardApi>()
    private val transactionApi = mockk<TransactionApi>()
    private val ruleRepository = mockk<ClassificationRuleRepository>()
    private val invoiceItemApi = mockk<InvoiceItemApi>()

    private val repo = RetrofitCardRepository(
        creditCardApi = creditCardApi,
        transactionApi = transactionApi,
        classificationRuleRepository = ruleRepository,
        invoiceItemApi = invoiceItemApi,
    )

    private val cardId = "card-1"
    private val invoiceId = "inv-1"

    private val cardDto = CreditCardDto(
        id = cardId,
        name = "Nubank",
        brand = "Visa",
        closingDay = 8,
    )

    @Before
    fun setUp() {
        // classifyPurchase reads the existing item to round-trip its name/amount; default to a
        // present item so the tags-only edit doesn't bail. Individual tests override as needed.
        coEvery { invoiceItemApi.getItems("inv1") } returns listOf(
            TransactionItemDto(id = "it1", idTransaction = "inv1", name = "iFood", amount = BigDecimal("50.00")),
        )
    }

    // -------------------------------------------------------------------------
    // getOpenInvoices — isInvoice path
    // -------------------------------------------------------------------------

    @Test
    fun `getOpenInvoices assembles invoice items from isInvoice tx sub-resource`() = runTest {
        val expenseTag = TagDto(id = "t1", name = "supermercado", kind = "EXPENSE", idCategory = "cat1")
        val invoiceTx = TransactionDto(
            id = invoiceId,
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = true,
            amount = BigDecimal("350.00"),
            datePaid = "2026-06-10",
        )
        val normalExpense = TransactionDto(
            id = "exp-2",
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = false,
            amount = BigDecimal("50.00"),
            name = "Café",
        )
        val item1 = TransactionItemDto(
            id = "it-1",
            idTransaction = invoiceId,
            name = "Supermercado Extra",
            amount = BigDecimal("200.00"),
            tags = listOf(expenseTag),
        )
        val item2 = TransactionItemDto(
            id = "it-2",
            idTransaction = invoiceId,
            name = "Farmácia",
            amount = BigDecimal("150.00"),
            tags = null,
        )

        coEvery { creditCardApi.getCards() } returns listOf(cardDto)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoiceTx, normalExpense)
        coEvery { invoiceItemApi.getItems(invoiceId) } returns listOf(item1, item2)

        val result = repo.getOpenInvoices()

        assertTrue(result.isSuccess)
        val invoices = result.getOrThrow()
        assertEquals(1, invoices.size)

        val invoice = invoices.single()
        assertEquals(2, invoice.items.size)

        // Total comes from the server-maintained invoice tx amount, NOT the sum of items
        assertEquals(BigDecimal("350.00"), invoice.total)

        val supermarketItem = invoice.items.first { it.name == "Supermercado Extra" }
        assertEquals(BigDecimal("200.00"), supermarketItem.amount)
        assertEquals("t1", supermarketItem.tags.single().id)
        // The item's category (idCategory → idContext) must survive the mapping so the UI can
        // resolve the chip color and show the classification.
        assertEquals("cat1", supermarketItem.tags.single().idContext)
        assertEquals("it-1", supermarketItem.itemId)
        assertEquals(invoiceId, supermarketItem.invoiceId)

        val pharmacyItem = invoice.items.first { it.name == "Farmácia" }
        assertEquals(BigDecimal("150.00"), pharmacyItem.amount)
        assertTrue(pharmacyItem.tags.isEmpty())
        assertEquals("it-2", pharmacyItem.itemId)

        // The non-isInvoice expense must NOT appear in invoice items
        assertTrue(invoice.items.none { it.name == "Café" })
    }

    @Test
    fun `getOpenInvoices anchors the due label to the data month, not the next month`() = runTest {
        val today = LocalDate.now()
        // Force the off-by-one trigger: a billDay strictly before today's day-of-month is exactly
        // what made the old closingDate roll into the NEXT month. The label must stay in the month
        // whose values are shown (the data / ref month), so June data reads "jun", never "jul".
        val billDay = (today.dayOfMonth - 1).coerceAtLeast(1)
        val cardWithBillDay = cardDto.copy(closingDay = billDay)
        val invoiceTx = TransactionDto(
            id = invoiceId,
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = true,
            amount = BigDecimal("100.00"),
        )
        val itemDto = TransactionItemDto(
            id = "it-1",
            idTransaction = invoiceId,
            name = "Netflix",
            amount = BigDecimal("100.00"),
        )

        coEvery { creditCardApi.getCards() } returns listOf(cardWithBillDay)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoiceTx)
        coEvery { invoiceItemApi.getItems(invoiceId) } returns listOf(itemDto)

        val invoice = repo.getOpenInvoices().getOrThrow().single()

        val monthAbbrev = today.withDayOfMonth(1)
            .format(DateTimeFormatter.ofPattern("MMM", Locale("pt", "BR")))
            .lowercase(Locale("pt", "BR"))
            .trimEnd('.')
        assertTrue(
            "dueLabel '${invoice.dueLabel}' must stay in the data month ($monthAbbrev)",
            invoice.dueLabel.endsWith(monthAbbrev),
        )
        assertEquals(BillingCycle.dueLabel(billDay, today.withDayOfMonth(1)), invoice.dueLabel)
    }

    @Test
    fun `getOpenInvoices invokes getItems once per invoice transaction`() = runTest {
        val invoiceTx = TransactionDto(
            id = invoiceId,
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = true,
            amount = BigDecimal("100.00"),
        )
        val itemDto = TransactionItemDto(
            id = "it-1",
            idTransaction = invoiceId,
            name = "Netflix",
            amount = BigDecimal("100.00"),
        )

        coEvery { creditCardApi.getCards() } returns listOf(cardDto)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoiceTx)
        coEvery { invoiceItemApi.getItems(invoiceId) } returns listOf(itemDto)

        repo.getOpenInvoices()

        coVerify(exactly = 1) { invoiceItemApi.getItems(invoiceId) }
    }

    // -------------------------------------------------------------------------
    // getOpenInvoices — fallback path (no isInvoice tx present)
    // -------------------------------------------------------------------------

    @Test
    fun `getOpenInvoices falls back to card credit expenses when no isInvoice tx exists`() = runTest {
        val fallbackExpense = TransactionDto(
            id = "exp-1",
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = false,
            name = "Loja A",
            amount = BigDecimal("120.00"),
            datePaid = "2026-06-05",
        )

        coEvery { creditCardApi.getCards() } returns listOf(cardDto)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(fallbackExpense)

        val result = repo.getOpenInvoices()

        assertTrue(result.isSuccess)
        val invoice = result.getOrThrow().single()
        assertEquals(1, invoice.items.size)

        val item = invoice.items.single()
        assertEquals("Loja A", item.name)
        assertEquals(BigDecimal("120.00"), item.amount)
        // itemId is null on the fallback path — no sub-resource was used
        assertNull(item.itemId)

        // getItems must NOT be called when there is no isInvoice tx
        coVerify(exactly = 0) { invoiceItemApi.getItems(any()) }
    }

    @Test
    fun `getOpenInvoices uses the invoice amount when the invoice has no line items`() = runTest {
        // A manual/projected fatura (e.g. a future month): the invoice header carries a total but has
        // no line items yet. The total must come from the invoice amount, not collapse to zero.
        val invoiceTx = TransactionDto(
            id = invoiceId,
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = true,
            amount = BigDecimal("935.63"),
        )

        coEvery { creditCardApi.getCards() } returns listOf(cardDto)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoiceTx)
        coEvery { invoiceItemApi.getItems(invoiceId) } returns emptyList()

        val result = repo.getOpenInvoices()

        assertTrue(result.isSuccess)
        val invoice = result.getOrThrow().single()
        assertEquals(0, BigDecimal("935.63").compareTo(invoice.total))
        assertTrue(invoice.items.isEmpty())
    }

    // -------------------------------------------------------------------------
    // datePurchase — the item's own purchase date, replacing the legacy name suffix
    // -------------------------------------------------------------------------

    /** Stubs one invoice holding [items], due 2026-06-10, so only the item mapping is under test. */
    private fun stubInvoiceWith(vararg items: TransactionItemDto) {
        val invoiceTx = TransactionDto(
            id = invoiceId,
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = true,
            amount = BigDecimal("100.00"),
            dateDue = "2026-06-10",
        )
        coEvery { creditCardApi.getCards() } returns listOf(cardDto)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoiceTx)
        coEvery { invoiceItemApi.getItems(invoiceId) } returns items.toList()
    }

    @Test
    fun `getOpenInvoices reads the item purchase date from datePurchase`() = runTest {
        stubInvoiceWith(
            TransactionItemDto(
                id = "it-1",
                idTransaction = invoiceId,
                name = "Padaria",
                amount = BigDecimal("30.00"),
                datePurchase = "2026-06-03",
            ),
        )

        val item = repo.getOpenInvoices().getOrThrow().single().items.single()

        assertEquals(LocalDate.of(2026, 6, 3), item.date)
        assertEquals("Padaria", item.name)
    }

    @Test
    fun `getOpenInvoices falls back to the legacy name suffix when datePurchase is null`() = runTest {
        // Items stored before the backend stopped stamping names still carry " · yyyy-MM-dd".
        stubInvoiceWith(
            TransactionItemDto(
                id = "it-1",
                idTransaction = invoiceId,
                name = "Padaria · 2026-06-03",
                amount = BigDecimal("30.00"),
                datePurchase = null,
            ),
        )

        val item = repo.getOpenInvoices().getOrThrow().single().items.single()

        assertEquals(LocalDate.of(2026, 6, 3), item.date)
        assertEquals("Padaria", item.name)
    }

    @Test
    fun `getOpenInvoices prefers datePurchase over a stale legacy name suffix`() = runTest {
        stubInvoiceWith(
            TransactionItemDto(
                id = "it-1",
                idTransaction = invoiceId,
                name = "Padaria · 2026-06-03",
                amount = BigDecimal("30.00"),
                datePurchase = "2026-06-07",
            ),
        )

        val item = repo.getOpenInvoices().getOrThrow().single().items.single()

        assertEquals(LocalDate.of(2026, 6, 7), item.date)
        // The suffix is still stripped from the display name even when it isn't the date source.
        assertEquals("Padaria", item.name)
    }

    @Test
    fun `getOpenInvoices falls back to the invoice date when the item has no date at all`() = runTest {
        stubInvoiceWith(
            TransactionItemDto(
                id = "it-1",
                idTransaction = invoiceId,
                name = "Padaria",
                amount = BigDecimal("30.00"),
                datePurchase = null,
            ),
        )

        val item = repo.getOpenInvoices().getOrThrow().single().items.single()

        assertEquals(LocalDate.of(2026, 6, 10), item.date)
    }

    @Test
    fun `classifyPurchase omits datePurchase so the backend preserves the stored one`() = runTest {
        // A partial PUT sends datePurchase = null, which the backend reads as "keep what is saved".
        // Sending null must never be used to clear the field.
        coEvery { invoiceItemApi.getItems("inv1") } returns listOf(
            TransactionItemDto(
                id = "it1",
                idTransaction = "inv1",
                name = "iFood",
                amount = BigDecimal("50.00"),
                datePurchase = "2026-06-03",
            ),
        )
        coEvery { invoiceItemApi.updateItem(any(), any(), any()) } returns "ok"

        repo.classifyPurchase(
            invoiceId = "inv1",
            itemId = "it1",
            tags = listOf(Tag(id = "t1", name = "x", kind = TransactionType.EXPENSE, idContext = "cat1")),
            learnRule = false,
        )

        coVerify(exactly = 1) {
            invoiceItemApi.updateItem("inv1", "it1", match { it.datePurchase == null })
        }
    }

    // -------------------------------------------------------------------------
    // classifyPurchase — tags PUT first, then the optional rule
    // -------------------------------------------------------------------------

    private val expenseTag = Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "cat1")

    private fun stubItemPut() {
        coEvery { invoiceItemApi.updateItem(any(), any(), any()) } returns "ok"
    }

    private suspend fun classify(tags: List<Tag>, learnRule: Boolean = true) =
        repo.classifyPurchase(invoiceId = "inv1", itemId = "it1", tags = tags, learnRule = learnRule)

    @Test
    fun `classifyPurchase PUTs the tags and creates a suggest rule keyed on the merchant`() = runTest {
        stubItemPut()
        coEvery { ruleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Saved)

        val outcome = classify(listOf(expenseTag)).getOrThrow()

        assertEquals(PurchaseClassifyOutcome.RuleCreated, outcome)
        coVerify(exactly = 1) {
            invoiceItemApi.updateItem("inv1", "it1", match { it.tagIds == listOf("t1") && it.idTransaction == "inv1" })
        }
        coVerify(exactly = 1) { ruleRepository.create(ClassificationRule.suggest("iFood", "t1")) }
    }

    @Test
    fun `classifyPurchase reports RuleAlreadyExisted when the rule is a duplicate`() = runTest {
        stubItemPut()
        coEvery { ruleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Duplicate)

        assertEquals(PurchaseClassifyOutcome.RuleAlreadyExisted, classify(listOf(expenseTag)).getOrThrow())
    }

    @Test
    fun `classifyPurchase reports RuleFailed, still succeeding, when the rule write fails`() = runTest {
        stubItemPut()
        coEvery { ruleRepository.create(any()) } returns Result.failure(RuntimeException("500"))

        val result = classify(listOf(expenseTag))

        assertTrue(result.isSuccess)
        assertEquals(PurchaseClassifyOutcome.RuleFailed, result.getOrThrow())
    }

    @Test
    fun `classifyPurchase without learnRule updates item tags and touches no rule`() = runTest {
        stubItemPut()

        val outcome = classify(listOf(expenseTag), learnRule = false).getOrThrow()

        assertEquals(PurchaseClassifyOutcome.TagsOnly, outcome)
        coVerify(exactly = 1) { invoiceItemApi.updateItem("inv1", "it1", any()) }
        coVerify(exactly = 0) { ruleRepository.create(any()) }
    }

    @Test
    fun `classifyPurchase teaches the first selected expense tag, keeping selection order`() = runTest {
        stubItemPut()
        coEvery { ruleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Saved)
        val income = Tag(id = "inc", name = "salário", kind = TransactionType.INCOME)
        val second = Tag(id = "t9", name = "lazer", kind = TransactionType.EXPENSE)

        classify(listOf(income, second, expenseTag))

        coVerify(exactly = 1) { ruleRepository.create(match { it.idTag == "t9" }) }
        coVerify(exactly = 1) { invoiceItemApi.updateItem(any(), any(), match { it.tagIds == listOf("inc", "t9", "t1") }) }
    }

    @Test
    fun `classifyPurchase teaches an expense tag that has no context`() = runTest {
        stubItemPut()
        coEvery { ruleRepository.create(any()) } returns Result.success(RuleWriteOutcome.Saved)
        val noContext = Tag(id = "t2", name = "geral", kind = TransactionType.EXPENSE, idContext = null)

        assertEquals(PurchaseClassifyOutcome.RuleCreated, classify(listOf(noContext)).getOrThrow())
    }

    @Test
    fun `classifyPurchase reports RuleFailed without a rule call when only income tags are selected`() = runTest {
        stubItemPut()
        val income = Tag(id = "inc", name = "salário", kind = TransactionType.INCOME)

        assertEquals(PurchaseClassifyOutcome.RuleFailed, classify(listOf(income)).getOrThrow())
        coVerify(exactly = 0) { ruleRepository.create(any()) }
    }

    @Test
    fun `classifyPurchase reports RuleFailed without a rule call when the item name sanitizes to a gateway marker`() = runTest {
        coEvery { invoiceItemApi.getItems("inv1") } returns listOf(
            TransactionItemDto(id = "it1", idTransaction = "inv1", name = "Ifd*", amount = BigDecimal("50.00")),
        )
        stubItemPut()

        assertEquals(PurchaseClassifyOutcome.RuleFailed, classify(listOf(expenseTag)).getOrThrow())
        coVerify(exactly = 0) { ruleRepository.create(any()) }
    }

    // -------------------------------------------------------------------------
    // addCard
    // -------------------------------------------------------------------------

    @Test
    fun `addCard posts a credit card dto and returns the mapped CreditCard`() = runTest {
        coEvery {
            creditCardApi.create(
                match { dto ->
                    dto.name == "Inter" &&
                        dto.brand == "Mastercard" &&
                        dto.closingDay == 15 &&
                        dto.color == "#8B00FF"
                },
            )
        } returns "new-card-id"
        coEvery { creditCardApi.getCards() } returns listOf(
            CreditCardDto(id = "new-card-id", name = "Inter", brand = "Mastercard", closingDay = 15, color = "#8B00FF"),
        )

        val result = repo.addCard(
            name = "Inter",
            brand = "Mastercard",
            closingDay = 15,
            color = "#8B00FF",
        )

        assertTrue(result.isSuccess)
        val domainCard = result.getOrThrow()
        assertEquals("new-card-id", domainCard.id)
        assertEquals("Inter", domainCard.name)
        assertEquals(15, domainCard.billDay)

        coVerify(exactly = 1) {
            creditCardApi.create(
                match { dto ->
                    dto.name == "Inter" &&
                        dto.brand == "Mastercard" &&
                        dto.closingDay == 15 &&
                        dto.color == "#8B00FF"
                },
            )
        }
    }

    @Test
    fun `addCard with null brand and color still posts minimal dto`() = runTest {
        coEvery {
            creditCardApi.create(
                match { dto -> dto.name == "Minimalista" && dto.brand == null && dto.color == null },
            )
        } returns "card-min"
        coEvery { creditCardApi.getCards() } returns listOf(
            CreditCardDto(id = "card-min", name = "Minimalista"),
        )

        val result = repo.addCard(name = "Minimalista", brand = null, closingDay = null, color = null)

        assertTrue(result.isSuccess)
        assertEquals("card-min", result.getOrThrow().id)
    }

    // -------------------------------------------------------------------------
    // getCards freshness — read live every time (no stale process-lifetime cache)
    // -------------------------------------------------------------------------

    @Test
    fun `getCards reads fresh on every call so a card added elsewhere shows up`() = runTest {
        coEvery { creditCardApi.getCards() } returns listOf(cardDto)

        repo.getCards()
        repo.getCards()

        // No caching: each read hits the api, so a card created on the web/another device is reflected.
        coVerify(exactly = 2) { creditCardApi.getCards() }
    }

    @Test
    fun `getCards does not cache a failed read so the next call retries the api`() = runTest {
        coEvery { creditCardApi.getCards() } throws RuntimeException("boom") andThen listOf(cardDto)

        val first = repo.getCards()
        val second = repo.getCards()

        assertTrue(first.isFailure)
        assertTrue(second.isSuccess)
        coVerify(exactly = 2) { creditCardApi.getCards() }
    }
}
