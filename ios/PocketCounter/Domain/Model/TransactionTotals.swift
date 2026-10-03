import Foundation

/// Income, expense and balance for a set of transactions. All statuses count, pending included.
/// Expense amounts are stored negative; `expense` here is their absolute value (a positive
/// magnitude), so `balance = income - expense`.
struct TransactionTotals: Hashable, Sendable {
    static let zero = TransactionTotals(income: .zero, expense: .zero, balance: .zero)

    let income: Money
    let expense: Money
    let balance: Money

    static func from(_ items: [HistoryItem]) -> TransactionTotals {
        let income = items.filter { $0.type == .income }.map(\.amount).sum()
        let expense = items.filter { $0.type == .expense }.map(\.amount.abs).sum()
        return TransactionTotals(income: income, expense: expense, balance: income - expense)
    }
}
