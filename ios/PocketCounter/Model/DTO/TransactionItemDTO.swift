import Foundation

struct TransactionItemDTO: Decodable, Sendable {
    let id: String
    let idTransaction: String
    let name: String
    let amount: Decimal
    let datePurchase: String?
    let originName: String?
    let tags: [TagDTO]?
}
