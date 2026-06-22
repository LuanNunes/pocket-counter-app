package com.resolveprogramming.pocketcounter.analytics

import com.resolveprogramming.pocketcounter.data.remote.api.TransactionApi
import com.resolveprogramming.pocketcounter.data.remote.dto.TagDto
import com.resolveprogramming.pocketcounter.data.remote.dto.TransactionDto
import com.resolveprogramming.pocketcounter.data.repository.RetrofitAnalyticsRepository
import com.resolveprogramming.pocketcounter.data.repository.SourceRepository
import com.resolveprogramming.pocketcounter.data.repository.TagRepository
import com.resolveprogramming.pocketcounter.domain.model.Source
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
 * Regression coverage for the Resumo↔Transações reconciliation rule: a despesa with no own tags
 * must INHERIT its source's default tags (and thus its context) when grouped, exactly like the
 * Transações ledger and Cartões invoice items do via [effectiveTagIds].
 *
 * Also covers the new income grouping contract: income transactions must group by their effective
 * kind=INCOME tag (id/name/color from the Tag catalog), NOT by idSource with a hardcoded color.
 */
class RetrofitAnalyticsRepositoryTest {

    private val transactionApi = mockk<TransactionApi>()
    private val tagRepository = mockk<TagRepository>()
    private val sourceRepository = mockk<SourceRepository>()

    private val repo = RetrofitAnalyticsRepository(transactionApi, tagRepository, sourceRepository)

    // --- income category tag used in the new income-grouping tests ---
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
        coEvery { sourceRepository.getAll() } returns Result.success(
            listOf(
                Source(
                    id = "s1",
                    name = "Mercado Pago",
                    idPaymentSource = "ps1",
                    allowsExpense = true,
                    allowsIncome = false,
                    tags = listOf("t1"), // source default tag → context c1
                ),
            ),
        )
    }

    /** Catalog with one EXPENSE tag/context AND one INCOME category tag. */
    private fun stubMixedCatalog(sourceTags: List<String> = emptyList()) {
        coEvery { tagRepository.getAllTags() } returns Result.success(
            listOf(
                Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "c1"),
                incomeTag,
            ),
        )
        coEvery { tagRepository.getAllContexts() } returns Result.success(
            listOf(TagContext(id = "c1", name = "Mercado", color = 0xFF112233)),
        )
        coEvery { sourceRepository.getAll() } returns Result.success(
            listOf(
                Source(
                    id = "s1",
                    name = "Nubank",
                    idPaymentSource = "ps1",
                    allowsExpense = true,
                    allowsIncome = true,
                    tags = sourceTags,
                ),
            ),
        )
    }

    private fun expense(idSource: String?, tags: List<TagDto>?) = TransactionDto(
        id = "e1",
        idSource = idSource,
        transactionType = "EXPENSE",
        amount = BigDecimal("100.00"),
        tags = tags,
    )

    private fun income(idSource: String?, tags: List<TagDto>?, amount: String = "3000.00") = TransactionDto(
        id = "i1",
        idSource = idSource,
        transactionType = "INCOME",
        amount = BigDecimal(amount),
        tags = tags,
    )

    @Test
    fun `expense with no own tags inherits its source context bucket`() = runTest {
        stubCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(expense("s1", tags = null))

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        val group = summary.groups.single()
        assertEquals("c1", group.id)
        assertEquals("Mercado", group.name)
        assertEquals(BigDecimal("100.00"), group.total)
    }

    @Test
    fun `explicit empty tag list is an override, not inheritance`() = runTest {
        stubCatalog()
        // tags == [] means "no tags" (override), so it must NOT inherit the source context.
        coEvery { transactionApi.getExpenses(any()) } returns listOf(expense("s1", tags = emptyList()))

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        assertEquals("Sem categoria", summary.groups.single().name)
    }

    @Test
    fun `own tag overrides the source default`() = runTest {
        coEvery { tagRepository.getAllTags() } returns Result.success(
            listOf(
                Tag(id = "t1", name = "supermercado", kind = TransactionType.EXPENSE, idContext = "c1"),
                Tag(id = "t2", name = "ifood", kind = TransactionType.EXPENSE, idContext = "c2"),
            ),
        )
        coEvery { tagRepository.getAllContexts() } returns Result.success(
            listOf(
                TagContext(id = "c1", name = "Mercado", color = 0xFF112233),
                TagContext(id = "c2", name = "Delivery", color = 0xFF445566),
            ),
        )
        coEvery { sourceRepository.getAll() } returns Result.success(
            listOf(
                Source("s1", "Mercado Pago", "ps1", allowsExpense = true, allowsIncome = false, tags = listOf("t1")),
            ),
        )
        coEvery { transactionApi.getExpenses(any()) } returns
            listOf(expense("s1", tags = listOf(TagDto(id = "t2", name = "ifood"))))

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        assertEquals("Delivery", summary.groups.single().name)
    }

    // -------------------------------------------------------------------------
    // New income grouping contract (RED tests — these FAIL against current code)
    // -------------------------------------------------------------------------

    /**
     * An income tx that carries a kind=INCOME tag must be bucketed by that tag's
     * id/name/color — NOT by idSource with the hardcoded 0xFF33AA77 green.
     *
     * Current code (classify, INCOME branch) returns Triple(idSource, sourceName, 0xFF33AA77),
     * so the group id will be "s1" and color 0xFF33AA77 instead of incomeTag.id/color.
     */
    @Test
    fun `income groups by income category tag with its own color`() = runTest {
        stubMixedCatalog()
        coEvery { transactionApi.getIncomes(any()) } returns listOf(
            income("s1", tags = listOf(TagDto(id = "it1", name = "Salário"))),
        )

        val summary = repo.summary("2026-06", TransactionType.INCOME, compareKey = null).getOrThrow()

        val group = summary.groups.single()
        assertEquals("it1", group.id)
        assertEquals("Salário", group.name)
        assertEquals(0xFF4A90D9L, group.color)
        assertEquals(BigDecimal("3000.00"), group.total)
    }

    /**
     * An income tx with no own tags must INHERIT its source's default tags. If those inherited
     * tags include a kind=INCOME tag it must land in that tag's bucket — not "Sem categoria".
     *
     * Current code ignores the source default tags for INCOME entirely; it buckets by idSource.
     */
    @Test
    fun `income inherits source income tag when it has no own tags`() = runTest {
        // Source s1 has incomeTag (it1) as its default tag.
        stubMixedCatalog(sourceTags = listOf("it1"))
        coEvery { transactionApi.getIncomes(any()) } returns listOf(
            income("s1", tags = null), // null → inherit source tags
        )

        val summary = repo.summary("2026-06", TransactionType.INCOME, compareKey = null).getOrThrow()

        val group = summary.groups.single()
        assertEquals("it1", group.id)
        assertEquals("Salário", group.name)
    }

    /**
     * An income tx whose effective tag list is empty (explicit override) or whose tags contain
     * no kind=INCOME entry must fall into the "Sem categoria" fallback bucket.
     *
     * Current code never produces "Sem categoria" for INCOME; it always uses idSource.
     */
    @Test
    fun `income with no resolvable income tag falls into Sem categoria`() = runTest {
        stubMixedCatalog()
        // tags = [] is an explicit empty override — no inheritance, no income tag.
        coEvery { transactionApi.getIncomes(any()) } returns listOf(
            income("s1", tags = emptyList()),
        )

        val summary = repo.summary("2026-06", TransactionType.INCOME, compareKey = null).getOrThrow()

        assertEquals("Sem categoria", summary.groups.single().name)
    }

    /**
     * An income tag (kind=INCOME, idContext=null) must NEVER appear as a group bucket when
     * classifying EXPENSE transactions — even when both tag kinds share the catalog.
     *
     * Current code doesn't use the tag catalog at all for INCOME, but after the fix a naive
     * "pick first tag regardless of kind" could bleed income tags into expense groups.
     */
    @Test
    fun `an income tag never appears in an expense context group`() = runTest {
        // Expense tx tagged with the INCOME category tag (realistic mis-tag edge case).
        // The expense grouping must ignore the income tag and fall into "Sem categoria",
        // never creating a bucket whose id matches the income tag.
        stubMixedCatalog()
        coEvery { transactionApi.getExpenses(any()) } returns listOf(
            expense("s1", tags = listOf(TagDto(id = "it1", name = "Salário"))),
        )

        val summary = repo.summary("2026-06", TransactionType.EXPENSE, compareKey = null).getOrThrow()

        val groupIds = summary.groups.map { it.id }
        assertFalse(
            "income tag 'it1' must not appear in expense groups",
            groupIds.contains("it1"),
        )
        // The expense tx has no resolvable EXPENSE context, so it falls into the fallback.
        assertEquals("Sem categoria", summary.groups.single().name)
    }
}
