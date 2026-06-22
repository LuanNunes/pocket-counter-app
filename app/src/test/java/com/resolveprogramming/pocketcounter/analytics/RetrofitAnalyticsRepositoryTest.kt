package com.resolveprogramming.pocketcounter.analytics

import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
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
    private val tagRepository = mockk<TagRepository>()

    private val repo = RetrofitAnalyticsRepository(transactionApi, tagRepository)

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
}
