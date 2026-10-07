import Foundation

@testable import PocketCounter

struct FakeTransactionRepository: TransactionRepository {
    var monthResult: Result<[HistoryItem], LoadFailure> = .success([])
    var rangeResult: Result<[HistoryItem], LoadFailure> = .success([])
    var invoiceItemsResult: Result<[InvoiceItem], LoadFailure> = .success([])
    var writeResult: Result<Void, WriteFailure> = .success(())
    var deleteResult: Result<Void, WriteFailure> = .success(())
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
