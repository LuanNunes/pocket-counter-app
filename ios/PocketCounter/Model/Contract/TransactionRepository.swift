import Foundation

/// Items come back in a total order: `refYearMonth`, incomes first, `displayOrder`, `date`, `id`.
protocol TransactionRepository: Sendable {
    func month(_ ref: RefYearMonth) async throws(LoadFailure) -> [HistoryItem]
    func range(_ span: RefYearMonthRange) async throws(LoadFailure) -> [HistoryItem]
    func invoiceItems(_ id: TransactionID) async throws(LoadFailure) -> [InvoiceItem]
}
