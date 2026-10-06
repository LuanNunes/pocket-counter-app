import Foundation

struct InvoiceItem: Hashable, Sendable {
    let id: InvoiceItemID
    let invoiceId: TransactionID
    let name: String
    /// Negative, like the invoice expense it belongs to.
    let amount: Money
    let purchasedOn: CalendarDay?
    let originName: String?
    /// `[]` means no tags; items have no override flag.
    let tagIds: [TagID]
}
