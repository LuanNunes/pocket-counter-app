import Foundation

/// Items come back in a total order: `refYearMonth`, incomes first, `displayOrder`, `date`, `id`.
protocol TransactionRepository: Sendable {
    func month(_ ref: RefYearMonth) async throws(LoadFailure) -> [HistoryItem]
    func range(_ span: RefYearMonthRange) async throws(LoadFailure) -> [HistoryItem]
    func invoiceItems(_ id: TransactionID) async throws(LoadFailure) -> [InvoiceItem]

    /// Sets rather than toggles, so a retry cannot flip the row back.
    func setPaymentStatus(_ status: PaymentStatus, on id: TransactionID) async throws(WriteFailure)

    /// A `.vanished` answer means the row was already gone.
    func delete(_ id: TransactionID) async throws(WriteFailure)

    /// `ids` is the complete order of one kind within a month; each id's index becomes its display order.
    /// Not atomic: a failure can leave the ids before the failing one already committed.
    func reorder(_ ids: [TransactionID]) async throws(WriteFailure)

    /// Answers nothing: a credit-card charge is stored as an invoice line item, so the POST
    /// answers the invoice's id and not the row's. Nothing here needs an id.
    func create(_ entry: TransactionEntry) async throws(WriteFailure)
}
