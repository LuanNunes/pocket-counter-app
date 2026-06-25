package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

class MonthFilterTest {

    private fun item(date: LocalDate) = HistoryItem(
        id = date.toString(),
        date = date,
        amount = BigDecimal("10"),
        type = TransactionType.INCOME,
        tagIds = emptyList(),
        statusPayment = PaymentStatus.PAID,
    )

    @Test
    fun `inMonth returns true for first day of target month`() {
        val june = YearMonth.of(2026, 6)

        assertTrue(HomeKpis.inMonth(item(LocalDate.of(2026, 6, 1)), june))
    }

    @Test
    fun `inMonth returns true for last day of target month`() {
        val june = YearMonth.of(2026, 6)

        assertTrue(HomeKpis.inMonth(item(LocalDate.of(2026, 6, 30)), june))
    }

    @Test
    fun `inMonth returns false for first day of next month`() {
        val june = YearMonth.of(2026, 6)

        assertFalse(HomeKpis.inMonth(item(LocalDate.of(2026, 7, 1)), june))
    }

    @Test
    fun `inMonth returns false for day in previous month`() {
        val june = YearMonth.of(2026, 6)

        assertFalse(HomeKpis.inMonth(item(LocalDate.of(2026, 5, 31)), june))
    }

    @Test
    fun `filterMonth keeps only items in target YearMonth`() {
        val june = YearMonth.of(2026, 6)
        val items = listOf(
            item(LocalDate.of(2026, 5, 31)),
            item(LocalDate.of(2026, 6, 1)),
            item(LocalDate.of(2026, 6, 30)),
            item(LocalDate.of(2026, 7, 1)),
        )

        val result = HomeKpis.filterMonth(items, june)

        assertEquals(2, result.size)
        assertTrue(result.all { YearMonth.from(it.date) == june })
    }

    @Test
    fun `filterMonth on empty list returns empty`() {
        val result = HomeKpis.filterMonth(emptyList(), YearMonth.of(2026, 6))

        assertEquals(emptyList<HistoryItem>(), result)
    }
}
