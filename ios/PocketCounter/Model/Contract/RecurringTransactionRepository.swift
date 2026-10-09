import Foundation

/// There is no `list()`: the app never lists recurring transactions.
protocol RecurringTransactionRepository: Sendable {
    /// Resolve-or-create: a name already in use answers with the existing recurring transaction.
    func create(_ draft: RecurringTransactionDraft) async throws(WriteFailure) -> RecurringTransaction

    /// Rewrites the row's tags on the server.
    func link(_ id: TransactionID, to recurring: RecurringTransactionID) async throws(WriteFailure)

    /// `.vanished` means the recurring transaction or the row is not there, or the row was not in it.
    func unlink(_ id: TransactionID, from recurring: RecurringTransactionID) async throws(WriteFailure)
}
