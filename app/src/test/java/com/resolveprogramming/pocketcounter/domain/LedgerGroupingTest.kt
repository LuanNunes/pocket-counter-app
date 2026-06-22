package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.Source
import com.resolveprogramming.pocketcounter.domain.model.Tag
import com.resolveprogramming.pocketcounter.domain.model.TagContext
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import com.resolveprogramming.pocketcounter.domain.model.groupLedger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class LedgerGroupingTest {

    private val palette = listOf(0xFF111111L, 0xFF222222L)

    private fun src(id: String, name: String, tags: List<String> = emptyList()) =
        Source(id, name, "pay", allowsExpense = true, allowsIncome = true, tags = tags)

    private fun expense(id: String, idSource: String, amount: String, ownTags: List<String>?) =
        HistoryItem(id, LocalDate.of(2026, 6, 13), idSource, "pay", BigDecimal(amount).negate(), TransactionType.EXPENSE, ownTags)

    private fun income(id: String, idSource: String, amount: String) =
        HistoryItem(id, LocalDate.of(2026, 6, 13), idSource, "pay", BigDecimal(amount), TransactionType.INCOME, emptyList())

    private val contexts = listOf(TagContext("c1", "Alimentação", 1L), TagContext("c2", "Transporte", 2L))

    // All tags used in grouping tests are expense tags — they have non-null idContext.
    private val tags = mapOf(
        "t1" to Tag("t1", "mercado", kind = TransactionType.EXPENSE, idContext = "c1"),
        "t2" to Tag("t2", "uber", kind = TransactionType.EXPENSE, idContext = "c2"),
    )

    @Test
    fun `CONTEXTO groups expenses by context order with Sem contexto last, incomes by subtotal desc`() {
        val sources = mapOf(
            "s1" to src("s1", "Mercado"),
            "s2" to src("s2", "Uber"),
            "s3" to src("s3", "Salário"),
            "s4" to src("s4", "Freela"),
        )
        val items = listOf(
            expense("e1", "s1", "100", listOf("t2")), // transporte
            expense("e2", "s2", "50", listOf("t1")),  // alimentação
            expense("e3", "s1", "30", null),           // no tags → Sem contexto
            income("i1", "s3", "5000"),
            income("i2", "s4", "8000"),
        )

        val groups = groupLedger(items, GroupMode.CONTEXTO, sources, tags, contexts, palette)

        // Expense context groups first, in context order, Sem contexto last; then incomes by subtotal desc.
        assertEquals(
            listOf("Alimentação", "Transporte", "Sem contexto", "Freela", "Salário"),
            groups.map { it.title },
        )
        assertEquals(BigDecimal("8000"), groups.first { it.title == "Freela" }.subtotal)
    }

    @Test
    fun `subtotals sum to the kind totals (reconciliation)`() {
        val sources = mapOf("s1" to src("s1", "A"), "s2" to src("s2", "B"))
        val items = listOf(
            expense("e1", "s1", "100", listOf("t1")),
            expense("e2", "s2", "200", null),
        )
        val groups = groupLedger(items, GroupMode.CONTEXTO, sources, tags, contexts, palette)
        val expenseSubtotalSum = groups.filter { it.type == TransactionType.EXPENSE }.fold(BigDecimal.ZERO) { a, g -> a + g.subtotal }
        assertEquals(BigDecimal("300"), expenseSubtotalSum)
    }

    @Test
    fun `TAG mode buckets by first effective tag, untagged last, no row duplicated`() {
        val sources = mapOf("s1" to src("s1", "A", tags = listOf("t1")))
        val items = listOf(
            expense("e1", "s1", "100", null),          // inherits t1 → mercado
            expense("e2", "s1", "50", emptyList()),    // explicit empty override → untagged
        )
        val groups = groupLedger(items, GroupMode.TAG, sources, tags, contexts, palette)

        assertEquals(listOf("mercado", "Sem tag"), groups.map { it.title })
        assertEquals(2, groups.sumOf { it.items.size }) // each row appears exactly once
    }

    @Test
    fun `LISTA mode returns no ledger groups`() {
        assertEquals(emptyList<Any>(), groupLedger(emptyList(), GroupMode.LISTA, emptyMap(), tags, contexts, palette))
    }

    // ── income tag does NOT get bucketed by context ──────────────────────────────────────────

    @Test
    fun `CONTEXTO mode income tag with null idContext does not create a context bucket`() {
        // An income tag (kind=INCOME, idContext=null) must never drive a context group.
        // LedgerGrouping only reads idContext from the tag map to resolve expense rows;
        // income rows are grouped by source regardless. This test proves the tag-kind distinction
        // does not cause a spurious context lookup when a tag happens to be INCOME-kind.
        val incomeTag = Tag("it1", "salário", kind = TransactionType.INCOME, idContext = null, color = 0xFF_001122L)
        val allTags = tags + mapOf("it1" to incomeTag)

        val sources = mapOf(
            "s1" to src("s1", "Empresa"),
            "s2" to src("s2", "Uber"),
        )
        val items = listOf(
            income("i1", "s1", "3000"),
            expense("e1", "s2", "40", listOf("t2")), // transporte
        )

        val groups = groupLedger(items, GroupMode.CONTEXTO, sources, allTags, contexts, palette)

        // No spurious "null" or extra context group must appear.
        // Expected: Transporte (expense), then Empresa (income by source).
        assertEquals(
            listOf("Transporte", "Empresa"),
            groups.map { it.title },
        )
        // Income group is by source, not by income tag.
        assertEquals(TransactionType.INCOME, groups.first { it.title == "Empresa" }.type)
    }
}
