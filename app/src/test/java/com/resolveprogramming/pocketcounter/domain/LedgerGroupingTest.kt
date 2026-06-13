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
    private val tags = mapOf(
        "t1" to Tag("t1", "mercado", "c1"),
        "t2" to Tag("t2", "uber", "c2"),
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
}
