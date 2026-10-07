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

struct SeriesID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}

struct ContextID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}

/// The server identifies users by UUID. `rawValue` stays the string it was given, not
/// `UUID.uuidString`, so the wire form is preserved.
struct UserID: RawRepresentable, Hashable, Sendable {
    let rawValue: String

    init?(rawValue: String) {
        guard UUID(uuidString: rawValue) != nil else { return nil }
        self.rawValue = rawValue
    }
}

struct InvoiceItemID: RawRepresentable, Hashable, Sendable {
    let rawValue: String
}
