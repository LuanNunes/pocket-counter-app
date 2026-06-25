package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.GroupMode
import com.resolveprogramming.pocketcounter.domain.model.GroupSort
import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
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

    private fun expense(id: String, amount: String, ownTags: List<String>?) =
        HistoryItem(id, LocalDate.of(2026, 6, 13), BigDecimal(amount).negate(), TransactionType.EXPENSE, ownTags)

    private fun income(id: String, amount: String, ownTags: List<String>?) =
        HistoryItem(id, LocalDate.of(2026, 6, 13), BigDecimal(amount), TransactionType.INCOME, ownTags)

    private val contexts = listOf(TagContext("c1", "Alimentação", 1L), TagContext("c2", "Transporte", 2L))

    private val tags = mapOf(
        "t1" to Tag("t1", "mercado", kind = TransactionType.EXPENSE, idContext = "c1"),
        "t2" to Tag("t2", "uber", kind = TransactionType.EXPENSE, idContext = "c2"),
        "i1" to Tag("i1", "Salário", kind = TransactionType.INCOME, idContext = null),
        "i2" to Tag("i2", "Freela", kind = TransactionType.INCOME, idContext = null),
    )

    @Test
    fun `CONTEXTO groups expenses by context order with Sem contexto last, incomes by category subtotal desc`() {
        val items = listOf(
            expense("e1", "100", listOf("t2")), // transporte
            expense("e2", "50", listOf("t1")),  // alimentação
            expense("e3", "30", null),           // no tags → Sem contexto
            income("inc1", "5000", listOf("i1")),
            income("inc2", "8000", listOf("i2")),
        )

        val groups = groupLedger(items, GroupMode.CONTEXTO, tags, contexts, palette)

        // Expense context groups first, in context order, Sem categoria last; then incomes by subtotal desc.
        assertEquals(
            listOf("Alimentação", "Transporte", "Sem categoria", "Freela", "Salário"),
            groups.map { it.title },
        )
        assertEquals(BigDecimal("8000"), groups.first { it.title == "Freela" }.subtotal)
    }

    @Test
    fun `CONTEXTO income with no income tag falls into Sem categoria`() {
        val items = listOf(
            income("inc1", "5000", listOf("i1")),
            income("inc2", "1000", null),
        )

        val groups = groupLedger(items, GroupMode.CONTEXTO, tags, contexts, palette)

        assertEquals(listOf("Salário", "Sem categoria"), groups.map { it.title })
    }

    @Test
    fun `subtotals sum to the kind totals (reconciliation)`() {
        val items = listOf(
            expense("e1", "100", listOf("t1")),
            expense("e2", "200", null),
        )
        val groups = groupLedger(items, GroupMode.CONTEXTO, tags, contexts, palette)
        val expenseSubtotalSum = groups.filter { it.type == TransactionType.EXPENSE }.fold(BigDecimal.ZERO) { a, g -> a + g.subtotal }
        assertEquals(BigDecimal("300"), expenseSubtotalSum)
    }

    @Test
    fun `TAG mode buckets by first effective tag, untagged last, no row duplicated`() {
        val items = listOf(
            expense("e1", "100", listOf("t1")),        // mercado
            expense("e2", "50", emptyList()),          // explicit empty override → untagged
        )
        val groups = groupLedger(items, GroupMode.TAG, tags, contexts, palette)

        assertEquals(listOf("mercado", "Sem tag"), groups.map { it.title })
        assertEquals(2, groups.sumOf { it.items.size }) // each row appears exactly once
    }

    @Test
    fun `LISTA mode returns no ledger groups`() {
        assertEquals(emptyList<Any>(), groupLedger(emptyList(), GroupMode.LISTA, tags, contexts, palette))
    }

    @Test
    fun `CONTEXTO SUBTOTAL_DESC sorts expense groups by subtotal descending`() {
        val items = listOf(
            expense("e1", "10", listOf("t1")),   // Alimentação subtotal = 10
            expense("e2", "200", listOf("t2")),  // Transporte subtotal = 200
            expense("e3", "5", null),            // Sem categoria subtotal = 5
        )

        val groups = groupLedger(
            items, GroupMode.CONTEXTO, tags, contexts, palette,
            expenseSort = GroupSort.SUBTOTAL_DESC,
        ).filter { it.type == TransactionType.EXPENSE }

        assertEquals(
            listOf("Transporte", "Alimentação", "Sem categoria"),
            groups.map { it.title },
        )
    }

    @Test
    fun `CONTEXTO CONTEXT_ORDER default preserves context-declaration order`() {
        val items = listOf(
            expense("e1", "10", listOf("t1")),   // Alimentação — context index 0
            expense("e2", "200", listOf("t2")),  // Transporte — context index 1
        )

        val groups = groupLedger(
            items, GroupMode.CONTEXTO, tags, contexts, palette,
            expenseSort = GroupSort.CONTEXT_ORDER,
        ).filter { it.type == TransactionType.EXPENSE }

        assertEquals(listOf("Alimentação", "Transporte"), groups.map { it.title })
    }
}
