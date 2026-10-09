import Foundation

/// Every field is optional so the mapper, not the decoder, decides what the entity cannot do without.
struct RecurringTransactionDTO: Decodable, Sendable {
    let id: String?
    let name: String?
    let transactionType: String?
}
