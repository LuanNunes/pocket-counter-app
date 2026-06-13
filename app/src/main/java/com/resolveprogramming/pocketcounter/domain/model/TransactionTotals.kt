package com.resolveprogramming.pocketcounter.domain.model

import java.math.BigDecimal

/**
 * Income / expense / balance for a set of transactions. The single source of truth for
 * monthly totals — Home and the Transações ledger both fold through [from] so their strips
 * can never drift. All statuses are counted (pending included), matching Home's saldo.
 *
 * Expense [HistoryItem.amount] carries a negative sign; [from] uses its absolute value so
 * `expense` is a positive magnitude and `balance = income − expense`.
 */
data class TransactionTotals(
    val income: BigDecimal,
    val expense: BigDecimal,
    val balance: BigDecimal,
) {
    companion object {
        val ZERO = TransactionTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)

        fun from(items: List<HistoryItem>): TransactionTotals {
            val income = items
                .filter { it.type == TransactionType.INCOME }
                .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount }
            val expense = items
                .filter { it.type == TransactionType.EXPENSE }
                .fold(BigDecimal.ZERO) { acc, item -> acc + item.amount.abs() }
            return TransactionTotals(income = income, expense = expense, balance = income - expense)
        }
    }
}
