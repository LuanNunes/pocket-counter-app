import Foundation

struct RecurringTransaction: Equatable, Sendable {
    let id: RecurringTransactionID
    let name: String
    let type: TransactionType
}
