import Foundation

/// `pendingTotal` is the sum of |amount| over PENDING expenses only: bills still to pay.
/// Pending income (receivables) is deliberately excluded so the figure reads as money owed.
struct HomeKpis: Hashable, Sendable {
    let totals: TransactionTotals
    let expenseCount: Int
    let incomeCount: Int
    let pendingTotal: Money
    let pendingCount: Int

    static func from(_ items: [HistoryItem]) -> HomeKpis {
        let pending = items.filter { $0.type == .expense && $0.statusPayment == .pending }
        return HomeKpis(
            totals: TransactionTotals.from(items),
            expenseCount: items.count { $0.type == .expense },
            incomeCount: items.count { $0.type == .income },
            pendingTotal: pending.map(\.amount.abs).sum(),
            pendingCount: pending.count
        )
    }
}
