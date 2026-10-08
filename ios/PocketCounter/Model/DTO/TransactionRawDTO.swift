import Foundation

struct TransactionRawRequestDTO: Encodable, Sendable {
    let text: String
    let referenceDate: String
}

/// Enums stay `String` so the mapper, not the decoder, decides what an unknown value costs.
struct TransactionRawResponseDTO: Decodable, Sendable {
    struct Reading: Decodable, Sendable {
        let type: String?
        let amount: Decimal?
        /// Text, as `TransactionDTO.dateDue`: a bad date is a mapping failure, not a decoding one.
        let date: String
        let name: String?
        let paymentMethod: String?
    }

    /// The server omits the keys of fields it did not read.
    struct Source: Decodable, Sendable {
        let type: String?
        let amount: String?
        let date: String
        let name: String?
        let paymentMethod: String?
        let card: String?
    }

    struct Card: Decodable, Sendable {
        let status: String
        let resolved: Candidate?
        let candidates: [Candidate]
    }

    struct Candidate: Decodable, Sendable {
        let id: String
        let name: String
    }

    struct Tag: Decodable, Sendable {
        let idTag: String?
        let idCategory: String?
    }

    let reading: Reading
    let source: Source
    let card: Card
    let tag: Tag
    let missing: [String]
}
