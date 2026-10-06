import Foundation

struct CreditCardDTO: Decodable, Sendable {
    let id: String
    let name: String
    let brand: String?
    let closingDay: Int?
    let color: String?
}
