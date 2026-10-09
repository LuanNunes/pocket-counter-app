import Foundation

@testable import PocketCounter

struct FakeRecurringTransactionRepository: RecurringTransactionRepository {
    enum Call: Equatable {
        case create(RecurringTransactionDraft)
        case link(TransactionID, RecurringTransactionID)
        case unlink(TransactionID, RecurringTransactionID)
    }

    var createResult: Result<RecurringTransaction, WriteFailure> = .success(
        RecurringTransaction(id: RecurringTransactionID(rawValue: "created"), name: "Aluguel", type: .expense))
    var linkResult: Result<Void, WriteFailure> = .success(())
    var unlinkResult: Result<Void, WriteFailure> = .success(())
    var log = CallLog()

    func create(_ draft: RecurringTransactionDraft) async throws(WriteFailure) -> RecurringTransaction {
        log.record(.create(draft))
        return try createResult.get()
    }

    func link(_ id: TransactionID, to recurring: RecurringTransactionID) async throws(WriteFailure) {
        log.record(.link(id, recurring))
        try linkResult.get()
    }

    func unlink(_ id: TransactionID, from recurring: RecurringTransactionID) async throws(WriteFailure) {
        log.record(.unlink(id, recurring))
        try unlinkResult.get()
    }

    final class CallLog: @unchecked Sendable {
        private let lock = NSLock()
        private var recorded: [Call] = []

        var calls: [Call] { lock.withLock { recorded } }

        func record(_ call: Call) { lock.withLock { recorded.append(call) } }
    }
}
