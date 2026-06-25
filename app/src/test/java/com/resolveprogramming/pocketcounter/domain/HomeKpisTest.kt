package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.HomeKpis
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class HomeKpisTest {

    private fun expense(
        id: String,
        amount: String,
        status: PaymentStatus = PaymentStatus.PAID,
    ) = HistoryItem(
        id = id,
        date = LocalDate.of(2026, 6, 13),
        amount = BigDecimal(amount).negate(),
        type = TransactionType.EXPENSE,
        tagIds = emptyList(),
        statusPayment = status,
    )

    private fun income(
        id: String,
        amount: String,
        status: PaymentStatus = PaymentStatus.PAID,
    ) = HistoryItem(
        id = id,
        date = LocalDate.of(2026, 6, 13),
        amount = BigDecimal(amount),
        type = TransactionType.INCOME,
        tagIds = emptyList(),
        statusPayment = status,
    )

    @Test
    fun `from empty list yields zero kpis`() {
        val kpis = HomeKpis.from(emptyList())

        assertEquals(BigDecimal.ZERO, kpis.totals.income)
        assertEquals(BigDecimal.ZERO, kpis.totals.expense)
        assertEquals(BigDecimal.ZERO, kpis.totals.balance)
        assertEquals(BigDecimal.ZERO, kpis.pendingTotal)
        assertEquals(0, kpis.pendingCount)
        assertEquals(0, kpis.expenseCount)
        assertEquals(0, kpis.incomeCount)
    }

    @Test
    fun `expenseCount and incomeCount count items by type`() {
        val items = listOf(
            expense("e1", "100"),
            expense("e2", "50"),
            income("i1", "200"),
        )

        val kpis = HomeKpis.from(items)

        assertEquals(2, kpis.expenseCount)
        assertEquals(1, kpis.incomeCount)
    }

    @Test
    fun `pendingTotal sums abs amounts of PENDING items across both types`() {
        val items = listOf(
            expense("e1", "100", PaymentStatus.PAID),
            expense("e2", "50", PaymentStatus.PENDING),
            income("i1", "200", PaymentStatus.PENDING),
            income("i2", "300", PaymentStatus.PAID),
        )

        val kpis = HomeKpis.from(items)

        // pending: |−50| + |200| = 250
        assertEquals(BigDecimal("250"), kpis.pendingTotal)
        assertEquals(2, kpis.pendingCount)
    }

    @Test
    fun `pendingTotal and pendingCount are zero when all items are paid`() {
        val items = listOf(
            expense("e1", "100", PaymentStatus.PAID),
            income("i1", "200", PaymentStatus.PAID),
        )

        val kpis = HomeKpis.from(items)

        assertEquals(BigDecimal.ZERO, kpis.pendingTotal)
        assertEquals(0, kpis.pendingCount)
    }

    @Test
    fun `totals delegate to TransactionTotals-from and are not re-folded`() {
        val items = listOf(
            expense("e1", "30"),
            income("i1", "80"),
        )

        val kpis = HomeKpis.from(items)

        assertEquals(BigDecimal("80"), kpis.totals.income)
        assertEquals(BigDecimal("30"), kpis.totals.expense)
        assertEquals(BigDecimal("50"), kpis.totals.balance)
    }
}
