import Foundation

/// `idTransaction` is deliberately absent: on invoice-item tags it holds the item id.
struct TagDTO: Decodable, Sendable {
    let id: String
    let name: String
    let kind: String
    let idCategory: String?
    let color: String?
    let idSeries: String?
}
