import Foundation

@testable import PocketCounter

struct FakeTransactionRepository: TransactionRepository {
    var monthResult: Result<[HistoryItem], LoadFailure> = .success([])
    var rangeResult: Result<[HistoryItem], LoadFailure> = .success([])
    var invoiceItemsResult: Result<[InvoiceItem], LoadFailure> = .success([])
    var rendezvous: Rendezvous?

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
}
