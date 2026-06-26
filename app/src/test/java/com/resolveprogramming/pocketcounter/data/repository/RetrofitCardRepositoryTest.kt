package com.resolveprogramming.pocketcounter.data.repository

/**
 * RED-phase tests for the faturas (Cartões invoices) phase.
 *
 * =====================================================================
 * ASSUMED PRODUCTION CONTRACT (implementer must provide exactly this):
 * =====================================================================
 *
 * 1. InvoiceItemApi (new Retrofit interface in data/remote/api/PocketApis.kt):
 *
 *    interface InvoiceItemApi {
 *        @GET("api/v1/transactions/{invoiceId}/items")
 *        suspend fun getItems(@Path("invoiceId") invoiceId: String): List<TransactionItemDto>
 *
 *        @PUT("api/v1/transactions/{invoiceId}/items/{itemId}")
 *        suspend fun updateItem(
 *            @Path("invoiceId") invoiceId: String,
 *            @Path("itemId") itemId: String,
 *            @Body dto: TransactionItemDto,
 *        ): String
 *
 *        @POST("api/v1/transactions/{invoiceId}/items")
 *        suspend fun addItem(
 *            @Path("invoiceId") invoiceId: String,
 *            @Body dto: TransactionItemDto,
 *        ): String
 *
 *        @DELETE("api/v1/transactions/{invoiceId}/items/{itemId}")
 *        suspend fun deleteItem(
 *            @Path("invoiceId") invoiceId: String,
 *            @Path("itemId") itemId: String,
 *        )
 *    }
 *
 * 2. TransactionItemDto (new @Serializable data class in data/remote/dto/ApiDtos.kt):
 *
 *    @Serializable
 *    data class TransactionItemDto(
 *        val id: String? = null,
 *        val idUser: String? = null,
 *        val idTransaction: String,
 *        val name: String,
 *        @Serializable(with = RemoteBigDecimalSerializer::class)
 *        val amount: BigDecimal,
 *        val tagIds: List<String>? = null,
 *        val tags: List<TagDto>? = null,
 *    )
 *
 * 3. ClassificationRuleDto NEW shape (replaces existing in data/remote/dto/ApiDtos.kt):
 *
 *    @Serializable
 *    data class ClassificationRuleDto(
 *        val id: String? = null,
 *        val patterns: List<String> = emptyList(),   // replaces `pattern: String`
 *        val matchType: String? = null,              // e.g. "CONTAINS"
 *        val active: Boolean? = null,
 *        val appliedCount: Int = 0,
 *        val transactionType: String? = null,        // "INCOME" | "EXPENSE"
 *        val paymentMethod: String? = null,          // PaymentMethodEnum name e.g. "CREDIT"
 *        val cardId: String? = null,
 *        val tagIds: List<ClassificationRuleTagDto> = emptyList(),
 *        // Legacy fields – kept null on write, ignored on read:
 *        val idPaymentSource: String? = null,
 *        val idSource: String? = null,
 *    )
 *
 * 4. ClassificationRuleTagDto is RENAMED field only – was `idContext`, now `idCategory`
 *    to match the verified backend contract:
 *
 *    @Serializable
 *    data class ClassificationRuleTagDto(
 *        val idTag: String,
 *        val idCategory: String,   // was idContext
 *    )
 *
 * 5. ClassificationRule domain model NEW shape (replaces existing in domain/model/ClassificationRule.kt):
 *
 *    data class ClassificationRule(
 *        val id: String?,
 *        val patterns: List<String>,           // replaces `pattern: String`
 *        val matchType: String?,
 *        val active: Boolean?,
 *        val appliedCount: Int,
 *        val transactionType: TransactionType?,
 *        val paymentMethod: PaymentMethod?,    // new
 *        val cardId: String?,                  // new
 *        val tags: List<Tag>,
 *        // Legacy fields dropped (idPaymentSource, idSource)
 *    )
 *
 * 6. InvoiceItem domain model GAINS two fields (in domain/model/OpenInvoice.kt):
 *
 *    data class InvoiceItem(
 *        val transactionId: String,   // still present; for isInvoice tx this is the invoice id
 *        val invoiceId: String,       // new: id of the parent invoice TransactionDto
 *        val itemId: String?,         // new: id of the TransactionItemDto (null on fallback path)
 *        val name: String,
 *        val date: LocalDate,
 *        val amount: BigDecimal,
 *        val tags: List<Tag>,
 *        val installmentLabel: String?,
 *    )
 *
 * 7. CardRepository.classifyPurchase NEW signature (in data/repository/CardRepository.kt):
 *
 *    suspend fun classifyPurchase(
 *        invoiceId: String,
 *        itemId: String,
 *        tags: List<Tag>,
 *        learnRule: Boolean,
 *        card: CreditCard,
 *    ): Result<ClassifyOutcome>
 *
 *    (The old `transactionId` param is replaced by two params: invoiceId + itemId.)
 *
 * 8. CardRepository.addCard NEW method (in data/repository/CardRepository.kt):
 *
 *    suspend fun addCard(
 *        name: String,
 *        brand: String?,
 *        closingDay: Int?,
 *        color: String?,
 *    ): Result<CreditCard>
 *
 * =====================================================================
 * FALLBACK RULE CHOSEN:
 *   When the isInvoice tx has zero items from the items sub-resource (getItems returns
 *   empty list), the invoice items are derived from the card's non-isInvoice credit
 *   expenses for that month (current behavior). itemId is null on these fallback items.
 *   If getItems returns a non-empty list, ONLY those items are used (no mixing).
 * =====================================================================
 */

import com.resolveprogramming.pocketcounter.data.remote.api.ClassificationRuleApi
import com.resolveprogramming.pocketcounter.data.remote.api.CreditCardApi
import com.resolveprogramming.pocketcounter.data.remote.api.InvoiceItemApi
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleDto
import com.resolveprogramming.pocketcounter.data.remote.dto.ClassificationRuleTagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.CreditCardDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionItemDto
import com.resolveprogramming.pocketcounter.domain.billing.BillingCycle
import com.resolveprogramming.pocketcounter.domain.model.CreditCard
import com.resolveprogramming.pocketcounter.domain.model.PaymentMethod
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val classificationRuleApi = mockk<ClassificationRuleApi>()
    private val invoiceItemApi = mockk<InvoiceItemApi>()

    private val repo = RetrofitCardRepository(
        creditCardApi = creditCardApi,
        transactionApi = transactionApi,
        classificationRuleApi = classificationRuleApi,
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

    private val card = CreditCard(
        id = cardId,
        name = "Nubank",
        brand = "Visa",
        last4 = "",
        gradientStart = 0L,
        gradientEnd = 0L,
        limit = BigDecimal.ZERO,
        billDay = 8,
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
    fun `getOpenInvoices falls back to card credit expenses when isInvoice tx has empty items`() = runTest {
        val invoiceTx = TransactionDto(
            id = invoiceId,
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = true,
            amount = BigDecimal("0.00"),
        )
        val creditExpense = TransactionDto(
            id = "exp-1",
            transactionType = "EXPENSE",
            paymentMethod = "CREDIT",
            cardId = cardId,
            isInvoice = false,
            name = "Streaming",
            amount = BigDecimal("40.00"),
        )

        coEvery { creditCardApi.getCards() } returns listOf(cardDto)
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoiceTx, creditExpense)
        coEvery { invoiceItemApi.getItems(invoiceId) } returns emptyList()

        val result = repo.getOpenInvoices()

        assertTrue(result.isSuccess)
        val invoice = result.getOrThrow().single()
        // Fell back: derives items from the non-isInvoice credit expenses
        assertEquals(1, invoice.items.size)
        assertEquals("Streaming", invoice.items.single().name)
        assertNull(invoice.items.single().itemId)
    }

    // -------------------------------------------------------------------------
    // classifyPurchase — new signature (invoiceId + itemId)
    // -------------------------------------------------------------------------

    @Test
    fun `classifyPurchase updates invoice item tags and creates rule in new shape`() = runTest {
        val expenseTag = Tag(
            id = "t1",
            name = "supermercado",
            kind = TransactionType.EXPENSE,
            idContext = "cat1",
        )

        coEvery { invoiceItemApi.updateItem(any(), any(), any()) } returns "ok"
        coEvery { classificationRuleApi.create(any()) } returns "rule-id-new"

        val result = repo.classifyPurchase(
            invoiceId = "inv1",
            itemId = "it1",
            tags = listOf(expenseTag),
            learnRule = true,
            card = card,
        )

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(outcome.ruleRequested)
        assertTrue(outcome.ruleCreated)

        // The item PUT must carry tagIds derived from the selected tags
        coVerify(exactly = 1) {
            invoiceItemApi.updateItem(
                "inv1",
                "it1",
                match { it.tagIds == listOf("t1") && it.idTransaction == "inv1" },
            )
        }

        // The rule POST must use the new ClassificationRuleDto shape
        coVerify(exactly = 1) {
            classificationRuleApi.create(
                match { dto ->
                    dto.patterns.isNotEmpty() &&
                        dto.matchType == "CONTAINS" &&
                        dto.transactionType == "EXPENSE" &&
                        dto.paymentMethod == "CREDIT" &&
                        dto.cardId == cardId &&
                        dto.tagIds == listOf(ClassificationRuleTagDto(idTag = "t1", idCategory = "cat1"))
                },
            )
        }
    }

    @Test
    fun `classifyPurchase without learnRule updates item tags but does not create a rule`() = runTest {
        val tag = Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "cat1")

        coEvery { invoiceItemApi.updateItem(any(), any(), any()) } returns "ok"

        val result = repo.classifyPurchase(
            invoiceId = "inv1",
            itemId = "it1",
            tags = listOf(tag),
            learnRule = false,
            card = card,
        )

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertFalse(outcome.ruleRequested)
        assertFalse(outcome.ruleCreated)

        coVerify(exactly = 1) { invoiceItemApi.updateItem("inv1", "it1", any()) }
        coVerify(exactly = 0) { classificationRuleApi.create(any()) }
    }

    @Test
    fun `classifyPurchase skips rule tags lacking a category and sets ruleCreated false`() = runTest {
        // tag with null idContext must be filtered out; with none remaining, no rule is posted
        val tagWithoutContext = Tag(
            id = "t2",
            name = "geral",
            kind = TransactionType.EXPENSE,
            idContext = null,
        )

        coEvery { invoiceItemApi.updateItem(any(), any(), any()) } returns "ok"

        val result = repo.classifyPurchase(
            invoiceId = "inv1",
            itemId = "it1",
            tags = listOf(tagWithoutContext),
            learnRule = true,
            card = card,
        )

        assertTrue(result.isSuccess)
        val outcome = result.getOrThrow()
        assertTrue(outcome.ruleRequested)
        assertFalse(outcome.ruleCreated)

        // Item tags were still persisted
        coVerify(exactly = 1) { invoiceItemApi.updateItem("inv1", "it1", any()) }
        // No rule created because all tags were filtered
        coVerify(exactly = 0) { classificationRuleApi.create(any()) }
    }

    @Test
    fun `classifyPurchase includes only categorized tags in rule tagIds`() = runTest {
        val categorized = Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "cat1")
        val uncategorized = Tag(id = "t2", name = "geral", kind = TransactionType.EXPENSE, idContext = null)

        coEvery { invoiceItemApi.updateItem(any(), any(), any()) } returns "ok"
        coEvery { classificationRuleApi.create(any()) } returns "rule-new"

        val result = repo.classifyPurchase(
            invoiceId = "inv1",
            itemId = "it1",
            tags = listOf(categorized, uncategorized),
            learnRule = true,
            card = card,
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().ruleCreated)

        // Rule tagIds must exclude the uncategorized tag
        coVerify(exactly = 1) {
            classificationRuleApi.create(
                match { dto ->
                    dto.tagIds == listOf(ClassificationRuleTagDto(idTag = "t1", idCategory = "cat1"))
                },
            )
        }
        // But item PUT carries both tags
        coVerify(exactly = 1) {
            invoiceItemApi.updateItem(
                any(),
                any(),
                match { it.tagIds == listOf("t1", "t2") },
            )
        }
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
    // ClassificationRule mapping — new DTO shape → domain
    // -------------------------------------------------------------------------

    @Test
    fun `ClassificationRuleDto new shape maps to domain with patterns matchType paymentMethod cardId`() = runTest {
        coEvery { classificationRuleApi.getAll() } returns listOf(
            ClassificationRuleDto(
                id = "r1",
                patterns = listOf("Supermercado", "Extra"),
                matchType = "CONTAINS",
                active = true,
                appliedCount = 5,
                transactionType = "EXPENSE",
                paymentMethod = "CREDIT",
                cardId = "card-1",
                tagIds = listOf(ClassificationRuleTagDto(idTag = "t1", idCategory = "cat1")),
            ),
        )

        // Indirect test via RetrofitClassificationRuleRepository which uses the same mapper
        val ruleRepo = RetrofitClassificationRuleRepository(classificationRuleApi)
        val rules = ruleRepo.getAll().getOrThrow()

        val rule = rules.single()
        assertEquals("r1", rule.id)
        assertEquals(listOf("Supermercado", "Extra"), rule.patterns)
        assertEquals("CONTAINS", rule.matchType)
        assertEquals(true, rule.active)
        assertEquals(5, rule.appliedCount)
        assertEquals(TransactionType.EXPENSE, rule.transactionType)
        assertEquals(PaymentMethod.CREDIT, rule.paymentMethod)
        assertEquals("card-1", rule.cardId)

        val ruleTag = rule.tags.single()
        assertEquals("t1", ruleTag.id)
        assertEquals("cat1", ruleTag.idContext)
    }

    @Test
    fun `ClassificationRuleDto with null paymentMethod and unknown matchType maps gracefully`() = runTest {
        coEvery { classificationRuleApi.getAll() } returns listOf(
            ClassificationRuleDto(
                id = "r2",
                patterns = listOf("Netflix"),
                matchType = null,
                active = null,
                appliedCount = 0,
                transactionType = "EXPENSE",
                paymentMethod = null,
                cardId = null,
                tagIds = emptyList(),
            ),
        )

        val ruleRepo = RetrofitClassificationRuleRepository(classificationRuleApi)
        val rule = ruleRepo.getAll().getOrThrow().single()

        assertEquals(listOf("Netflix"), rule.patterns)
        assertNull(rule.matchType)
        assertNull(rule.active)
        assertEquals(0, rule.appliedCount)
        assertNull(rule.paymentMethod)
        assertNull(rule.cardId)
        assertTrue(rule.tags.isEmpty())
    }
}
