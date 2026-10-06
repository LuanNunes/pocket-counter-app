import Foundation

/// `tags == nil` carries what the backend's `hasTagOverride` (never on the wire) would say.
struct TransactionDTO: Decodable, Sendable {
    let id: String
    let transactionType: String
    let name: String?
    let description: String?
    let amount: Decimal
    let statusPayment: String
    let refYearMonth: Int
    let displayOrder: Int
    let paymentMethod: String?
    let cardId: String?
    let isInvoice: Bool
    let idSeries: String?
    let dateDue: String?
    let datePaid: String?
    let tags: [TagDTO]?
}
