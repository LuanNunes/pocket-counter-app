import Foundation

@testable import PocketCounter

struct FakeTransactionRepository: TransactionRepository {
    var monthResult: Result<[HistoryItem], LoadFailure> = .success([])
    var rangeResult: Result<[HistoryItem], LoadFailure> = .success([])
    var invoiceItemsResult: Result<[InvoiceItem], LoadFailure> = .success([])
    var writeResult: Result<Void, WriteFailure> = .success(())
    var deleteResult: Result<Void, WriteFailure> = .success(())
    var createResult: Result<Void, WriteFailure> = .success(())
    var created = CreatedEntryLog()
    var writes = StatusWriteLog()
    var rendezvous: Rendezvous?
    var writeRendezvous: Rendezvous?

    func month(_ ref: RefYearMonth) async throws(LoadFailure) -> [HistoryItem] {
        await rendezvous?.arrive()
        return try monthResult.get()
    }

    func range(_ span: RefYearMonthRange) async throws(LoadFailure) -> [HistoryItem] {
        await rendezvous?.arrive()
        return try rangeResult.get()
    }

    func invoiceItems(_ id: TransactionID) async throws(LoadFailure) -> [InvoiceItem] {
        try invoiceItemsResult.get()
    }

    func setPaymentStatus(_ status: PaymentStatus, on id: TransactionID) async throws(WriteFailure) {
        writes.record(id, status)
        await writeRendezvous?.arrive()
        try writeResult.get()
    }

    func delete(_ id: TransactionID) async throws(WriteFailure) {
        try deleteResult.get()
    }

    func reorder(_ ids: [TransactionID]) async throws(WriteFailure) {}

    func create(_ entry: TransactionEntry) async throws(WriteFailure) {
        created.record(entry)
        await writeRendezvous?.arrive()
        try createResult.get()
    }
}

final class StatusWriteLog: @unchecked Sendable {
    private let lock = NSLock()
    private var recorded: [Call] = []

    struct Call: Equatable {
        let id: TransactionID
        let status: PaymentStatus
    }

    var calls: [Call] { lock.withLock { recorded } }

    func record(_ id: TransactionID, _ status: PaymentStatus) {
        lock.withLock { recorded.append(Call(id: id, status: status)) }
    }
}

final class CreatedEntryLog: @unchecked Sendable {
    private let lock = NSLock()
    private var recorded: [TransactionEntry] = []

    var entries: [TransactionEntry] { lock.withLock { recorded } }

    func record(_ entry: TransactionEntry) {
        lock.withLock { recorded.append(entry) }
    }
}
