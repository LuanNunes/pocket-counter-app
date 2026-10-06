import Foundation

struct CategoryDTO: Decodable, Sendable {
    let id: String
    let name: String
    let color: String?
    let displayOrder: Int?
}
