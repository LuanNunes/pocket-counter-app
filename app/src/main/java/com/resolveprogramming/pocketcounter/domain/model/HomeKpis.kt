package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal
import java.time.YearMonth

/**
 * Derived KPIs for the Home screen, computed from a list of [HistoryItem]s.
 *
 * [totals] delegates directly to [TransactionTotals.from] — do not re-fold income/expense.
 * [pendingTotal] is the sum of |amount| over items whose [HistoryItem.statusPayment] is
 * [PaymentStatus.PENDING], across both types. [pendingCount] is the count of those items.
 */
data class HomeKpis(
    val totals: TransactionTotals,
    val expenseCount: Int,
    val incomeCount: Int,
    val pendingTotal: BigDecimal,
    val pendingCount: Int,
) {
    companion object {

        fun from(items: List<HistoryItem>): HomeKpis {
            val pending = items.filter { it.statusPayment == PaymentStatus.PENDING }
            return HomeKpis(
                totals = TransactionTotals.from(items),
                expenseCount = items.count { it.type == TransactionType.EXPENSE },
                incomeCount = items.count { it.type == TransactionType.INCOME },
                pendingTotal = pending.fold(BigDecimal.ZERO) { acc, item -> acc + item.amount.abs() },
                pendingCount = pending.size,
            )
        }

        fun inMonth(item: HistoryItem, month: YearMonth): Boolean =
            YearMonth.from(item.date) == month

        fun filterMonth(items: List<HistoryItem>, month: YearMonth): List<HistoryItem> =
            items.filter { inMonth(it, month) }
    }
}
