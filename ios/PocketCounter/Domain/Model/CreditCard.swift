import Foundation

struct CreditCard: Hashable, Sendable {
    let id: CardID
    let name: String
    let brand: String
    let last4: String
    let limit: Money
    let billDay: Int
}
