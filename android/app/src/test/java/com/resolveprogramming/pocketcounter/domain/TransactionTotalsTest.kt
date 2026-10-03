package com.resolveprogramming.pocketcounter.domain

import com.resolveprogramming.pocketcounter.domain.model.HistoryItem
import com.resolveprogramming.pocketcounter.domain.model.PaymentStatus
import com.resolveprogramming.pocketcounter.domain.model.TransactionTotals
import com.resolveprogramming.pocketcounter.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class TransactionTotalsTest {

    private fun item(
        amount: String,
        type: TransactionType,
        status: PaymentStatus = PaymentStatus.PAID,
    ) = HistoryItem(
        id = amount + type,
        date = LocalDate.of(2026, 6, 13),
        amount = BigDecimal(amount),
        type = type,
        tagIds = emptyList(),
        statusPayment = status,
    )

    @Test
    fun `income is summed, expense uses absolute value, balance is the difference`() {
        val items = listOf(
            item("100.00", TransactionType.INCOME),
            item("50.00", TransactionType.INCOME),
            // Expense amounts carry a negative sign in HistoryItem.
            item("-30.00", TransactionType.EXPENSE),
            item("-20.00", TransactionType.EXPENSE),
        )

        val totals = TransactionTotals.from(items)

        assertEquals(BigDecimal("150.00"), totals.income)
        assertEquals(BigDecimal("50.00"), totals.expense)
        assertEquals(BigDecimal("100.00"), totals.balance)
    }

    @Test
    fun `pending transactions are included in totals`() {
        val items = listOf(
            item("80.00", TransactionType.INCOME, PaymentStatus.PENDING),
            item("-25.00", TransactionType.EXPENSE, PaymentStatus.PENDING),
        )

        val totals = TransactionTotals.from(items)

        assertEquals(BigDecimal("80.00"), totals.income)
        assertEquals(BigDecimal("25.00"), totals.expense)
        assertEquals(BigDecimal("55.00"), totals.balance)
    }

    @Test
    fun `empty list yields zero totals`() {
        val totals = TransactionTotals.from(emptyList())
        assertEquals(0, totals.income.signum())
        assertEquals(0, totals.expense.signum())
        assertEquals(0, totals.balance.signum())
    }
}
