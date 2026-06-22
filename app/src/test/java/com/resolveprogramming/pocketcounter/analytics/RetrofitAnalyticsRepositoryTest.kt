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
import org.junit.Test
import java.math.BigDecimal

/**
 * Regression coverage for the Resumo↔Transações reconciliation rule: a despesa with no own tags
 * must INHERIT its source's default tags (and thus its context) when grouped, exactly like the
 * Transações ledger and Cartões invoice items do via [effectiveTagIds].
 */
class RetrofitAnalyticsRepositoryTest {

    private val transactionApi = mockk<TransactionApi>()
    private val tagRepository = mockk<TagRepository>()
    private val sourceRepository = mockk<SourceRepository>()

    private val repo = RetrofitAnalyticsRepository(transactionApi, tagRepository, sourceRepository)

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

    private fun expense(idSource: String?, tags: List<TagDto>?) = TransactionDto(
        id = "e1",
        idSource = idSource,
        transactionType = "EXPENSE",
        amount = BigDecimal("100.00"),
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
}
