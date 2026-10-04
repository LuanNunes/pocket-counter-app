import Foundation

struct TransactionID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}

struct TagID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}

struct CardID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}

struct ContextID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}
