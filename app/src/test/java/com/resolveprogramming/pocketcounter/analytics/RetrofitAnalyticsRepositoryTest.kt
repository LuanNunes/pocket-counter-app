package com.resolveprogramming.pocketcounter.analytics

import com.resolveprogramming.pocketcounter.data.remote.api.InvoiceItemApi
import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionItemDto
import com.resolveprogramming.pocketcounter.data.repository.RetrofitAnalyticsRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.math.BigDecimal

/**
 * Resumo grouping contract: expenses bucket by their own first tag's context (falling back to
 * "Sem categoria"); incomes bucket by their effective kind=INCOME tag (id/name/color from the
 * Tag catalog), falling back to "Sem categoria".
 */
class RetrofitAnalyticsRepositoryTest {

    private val transactionApi = mockk<TransactionApi>()
    private val invoiceItemApi = mockk<InvoiceItemApi>()
    private val tagRepository = mockk<TagRepository>()

    private val repo = RetrofitAnalyticsRepository(transactionApi, invoiceItemApi, tagRepository)

    private val incomeTag = Tag(
        id = "it1",
        name = "Salário",
        kind = TransactionType.INCOME,
        idContext = null,
        color = 0xFF4A90D9,
    )

    private fun stubCatalog() {
        coEvery { tagRepository.getAllTags() } returns Result.success(
            listOf(Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "c1")),
        )
        coEvery { tagRepository.getAllContexts() } returns Result.success(
            listOf(TagContext(id = "c1", name = "Mercado", color = 0xFF112233)),
        )
    }

    /** Catalog with one EXPENSE tag/context AND one INCOME category tag. */
    private fun stubMixedCatalog() {
        coEvery { tagRepository.getAllTags() } returns Result.success(
            listOf(
                Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "c1"),
                incomeTag,
            ),
        )
        coEvery { tagRepository.getAllContexts() } returns Result.success(
            listOf(TagContext(id = "c1", name = "Mercado", color = 0xFF112233)),
        )
    }

    private fun expense(tags: List<TagDto>?) = TransactionDto(
        id = "e1",
        transactionType = "EXPENSE",
        amount = BigDecimal("100.00"),
        tags = tags,
    )

    private fun income(tags: List<TagDto>?, amount: String = "3000.00") = TransactionDto(
        id = "i1",
        transactionType = "INCOME",
        amount = BigDecimal(amount),
        tags = tags,
    )

    @Test
    fun `expense with own tag groups by its context`() = runTest {
        stubCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns
            listOf(expense(tags = listOf(TagDto(id = "t1", name = "supermercado"))))

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        val group = summary.groups.single()
        assertEquals("c1", group.id)
        assertEquals("Mercado", group.name)
        assertEquals(BigDecimal("100.00"), group.total)
    }

    @Test
    fun `expense with no tags falls into Sem categoria`() = runTest {
        stubCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(expense(tags = null))

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        assertEquals("Sem categoria", summary.groups.single().name)
    }

    // -------------------------------------------------------------------------
    // Income grouping
    // -------------------------------------------------------------------------

    @Test
    fun `income groups by income category tag with its own color`() = runTest {
        stubMixedCatalog()
        coEvery { transactionApi.getIncomes(any()) } returns listOf(
            income(tags = listOf(TagDto(id = "it1", name = "Salário"))),
        )

        val summary = repo.summary("2026-06", TransactionType.INCOME, compareKey = null).getOrThrow()

        val group = summary.groups.single()
        assertEquals("it1", group.id)
        assertEquals("Salário", group.name)
        assertEquals(0xFF4A90D9L, group.color)
        assertEquals(BigDecimal("3000.00"), group.total)
    }

    @Test
    fun `income with no resolvable income tag falls into Sem categoria`() = runTest {
        stubMixedCatalog()
        coEvery { transactionApi.getIncomes(any()) } returns listOf(
            income(tags = null),
        )

        val summary = repo.summary("2026-06", TransactionType.INCOME, compareKey = null).getOrThrow()

        assertEquals("Sem categoria", summary.groups.single().name)
    }

    @Test
    fun `an income tag never appears in an expense context group`() = runTest {
        // Expense tx mis-tagged with the INCOME category tag must ignore it and fall into the
        // fallback, never creating a bucket whose id matches the income tag.
        stubMixedCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(
            expense(tags = listOf(TagDto(id = "it1", name = "Salário"))),
        )

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        val groupIds = summary.groups.map { it.id }
        assertFalse(
            "income tag 'it1' must not appear in expense groups",
            groupIds.contains("it1"),
        )
        assertEquals("Sem categoria", summary.groups.single().name)
    }

    // -------------------------------------------------------------------------
    // Credit-card invoice expansion
    // -------------------------------------------------------------------------

    /** Catalog with two EXPENSE tags mapping to two distinct contexts. */
    private fun stubTwoContextCatalog() {
        coEvery { tagRepository.getAllTags() } returns Result.success(
            listOf(
                Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "c1"),
                Tag(id = "t2", name = "farmácia", kind = TransactionType.EXPENSE, idContext = "c2"),
            ),
        )
        coEvery { tagRepository.getAllContexts() } returns Result.success(
            listOf(
                TagContext(id = "c1", name = "Mercado", color = 0xFF112233),
                TagContext(id = "c2", name = "Saúde", color = 0xFF445566),
            ),
        )
    }

    private fun invoice(amount: String, id: String = "inv1") = TransactionDto(
        id = id,
        transactionType = "EXPENSE",
        amount = BigDecimal(amount),
        isInvoice = true,
        cardId = "card1",
    )

    private fun item(amount: String, tagId: String, tagName: String) = TransactionItemDto(
        idTransaction = "inv1",
        name = tagName,
        amount = BigDecimal(amount),
        tags = listOf(TagDto(id = tagId, name = tagName)),
    )

    @Test
    fun `invoice expands into its items breaking down by each item's own context`() = runTest {
        stubTwoContextCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoice(amount = "150.00"))
        coEvery { invoiceItemApi.getItems("inv1") } returns listOf(
            item(amount = "100.00", tagId = "t1", tagName = "supermercado"),
            item(amount = "50.00", tagId = "t2", tagName = "farmácia"),
        )

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        // Total reflects the items, and (here) equals the container only because items sum to it.
        assertEquals(BigDecimal("150.00"), summary.total)
        val byId = summary.groups.associateBy { it.id }
        assertEquals(BigDecimal("100.00"), byId.getValue("c1").total)
        assertEquals(BigDecimal("50.00"), byId.getValue("c2").total)
    }

    @Test
    fun `invoice container amount is replaced not added to the items`() = runTest {
        // Container amount (999) is a red herring — totals must come from the items (30), never both.
        stubTwoContextCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoice(amount = "999.00"))
        coEvery { invoiceItemApi.getItems("inv1") } returns listOf(
            item(amount = "30.00", tagId = "t1", tagName = "supermercado"),
        )

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        assertEquals(BigDecimal("30.00"), summary.total)
        assertEquals(BigDecimal("30.00"), summary.groups.single().total)
    }

    @Test
    fun `non-invoice expense alongside an invoice is left untouched`() = runTest {
        stubTwoContextCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(
            invoice(amount = "150.00"),
            TransactionDto(
                id = "e9",
                transactionType = "EXPENSE",
                amount = BigDecimal("20.00"),
                tags = listOf(TagDto(id = "t1", name = "supermercado")),
            ),
        )
        coEvery { invoiceItemApi.getItems("inv1") } returns listOf(
            item(amount = "100.00", tagId = "t1", tagName = "supermercado"),
            item(amount = "50.00", tagId = "t2", tagName = "farmácia"),
        )

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        assertEquals(BigDecimal("170.00"), summary.total)
        val byId = summary.groups.associateBy { it.id }
        assertEquals(BigDecimal("120.00"), byId.getValue("c1").total)
        assertEquals(BigDecimal("50.00"), byId.getValue("c2").total)
    }

    @Test
    fun `invoice with zero items falls back to the container amount`() = runTest {
        stubTwoContextCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(invoice(amount = "80.00"))
        coEvery { invoiceItemApi.getItems("inv1") } returns emptyList()

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        assertEquals(BigDecimal("80.00"), summary.total)
        // No item tags → the container itself is untagged → Sem categoria.
        assertEquals("Sem categoria", summary.groups.single().name)
    }
}
