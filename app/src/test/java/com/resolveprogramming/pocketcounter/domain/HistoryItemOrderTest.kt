package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HistoryItemOrderTest {

    private val june4 = LocalDate.of(2026, 6, 4)
    private val june10 = LocalDate.of(2026, 6, 10)
    private val june15 = LocalDate.of(2026, 6, 15)

    private fun makeItem(
        id: String,
        displayOrder: Int = 0,
        date: LocalDate = june4,
    ) = HistoryItem(
        id = id,
        date = date,
        amount = BigDecimal("100.00"),
        type = TransactionType.EXPENSE,
        tagIds = null,
        statusPayment = PaymentStatus.PAID,
        displayOrder = displayOrder,
    )

    // -------------------------------------------------------------------------
    // 1. displayOrder ascending when displayOrder differs
    // -------------------------------------------------------------------------

    @Test
    fun `LEDGER_ORDER sorts by displayOrder ascending`() {
        val higher = makeItem(id = "high", displayOrder = 2)
        val lower = makeItem(id = "low", displayOrder = 0)

        val sorted = listOf(higher, lower).sortedWith(HistoryItem.LEDGER_ORDER)

        assertEquals(listOf("low", "high"), sorted.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 2. Same displayOrder, different dates → newer date first
    // -------------------------------------------------------------------------

    @Test
    fun `LEDGER_ORDER sorts by date descending when displayOrder ties`() {
        val older = makeItem(id = "old", date = june4)
        val newer = makeItem(id = "new", date = june10)

        val sorted = listOf(older, newer).sortedWith(HistoryItem.LEDGER_ORDER)

        assertEquals(listOf("new", "old"), sorted.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 3. Same displayOrder AND same date → ascending by id (the core bug case)
    // -------------------------------------------------------------------------

    @Test
    fun `LEDGER_ORDER sorts by id ascending when displayOrder and date both tie`() {
        val beta = makeItem(id = "beta")
        val alpha = makeItem(id = "alpha")

        val sorted = listOf(beta, alpha).sortedWith(HistoryItem.LEDGER_ORDER)

        assertEquals(listOf("alpha", "beta"), sorted.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 4. Determinism under shuffle: identical output regardless of input order
    // -------------------------------------------------------------------------

    @Test
    fun `LEDGER_ORDER produces identical output regardless of input order`() {
        val items = listOf(
            makeItem(id = "item-05"),
            makeItem(id = "item-01"),
            makeItem(id = "item-03"),
            makeItem(id = "item-02"),
            makeItem(id = "item-04"),
        )
        val reversed = items.reversed()
        val reordered = listOf(items[2], items[0], items[4], items[1], items[3])

        val expected = items.sortedWith(HistoryItem.LEDGER_ORDER).map { it.id }

        assertEquals(expected, reversed.sortedWith(HistoryItem.LEDGER_ORDER).map { it.id })
        assertEquals(expected, reordered.sortedWith(HistoryItem.LEDGER_ORDER).map { it.id })
    }

    // -------------------------------------------------------------------------
    // 5. Idempotence: sorting an already-sorted list returns the same order
    // -------------------------------------------------------------------------

    @Test
    fun `LEDGER_ORDER is idempotent`() {
        val items = listOf(
            makeItem(id = "a"),
            makeItem(id = "b"),
            makeItem(id = "c"),
        )

        val onceSorted = items.sortedWith(HistoryItem.LEDGER_ORDER)
        val twiceSorted = onceSorted.sortedWith(HistoryItem.LEDGER_ORDER)

        assertEquals(onceSorted.map { it.id }, twiceSorted.map { it.id })
    }

    // -------------------------------------------------------------------------
    // 6. Mixed realistic set — one expected sequence
    // -------------------------------------------------------------------------

    @Test
    fun `LEDGER_ORDER produces expected sequence for mixed realistic items`() {
        // displayOrder=0: two on june10 (ids "a" then "c"), one on june4 (id "b")
        // displayOrder=1: one on june15 (id "a-hi"), one on june10 (id "b-hi")
        val itemA10 = makeItem(id = "a",    displayOrder = 0, date = june10)
        val itemC10 = makeItem(id = "c",    displayOrder = 0, date = june10)
        val itemB04 = makeItem(id = "b",    displayOrder = 0, date = june4)
        val itemA15 = makeItem(id = "a-hi", displayOrder = 1, date = june15)
        val itemB10 = makeItem(id = "b-hi", displayOrder = 1, date = june10)

        // Feed in scrambled order
        val input = listOf(itemA15, itemB04, itemC10, itemB10, itemA10)

        val sorted = input.sortedWith(HistoryItem.LEDGER_ORDER).map { it.id }

        // displayOrder=0 first, newest-date-first within tier, id-asc within same date;
        // then displayOrder=1 newest-first.
        assertEquals(listOf("a", "c", "b", "a-hi", "b-hi"), sorted)
    }
}
