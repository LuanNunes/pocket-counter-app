import Foundation

/// A transaction the user confirmed. No optionals where the server requires a value, and
/// stricter than it on purpose: the server refuses an empty name untrimmed and lets a zero
/// amount through (`TransactionDto.kt:195-196`). A name of spaces reaches here untrimmed —
/// the mapper passes the server's name through raw — so this init is that path's only guard.
struct TransactionEntry: Hashable, Sendable {
    let type: TransactionType
    /// A magnitude. The endpoint carries the direction.
    let amount: Money
    let date: CalendarDay
    let name: String
    let paymentMethod: PaymentMethod?
    let card: CardID?
    let tag: TagID?
    let allowDuplicate: Bool

    /// UTF-16 units, the strictest candidate unit: never accepts a name the column would reject.
    static let maxNameUTF16Units = 250

    init?(
        type: TransactionType,
        amount: Money,
        date: CalendarDay,
        name: String,
        paymentMethod: PaymentMethod? = nil,
        card: CardID? = nil,
        tag: TagID? = nil,
        allowDuplicate: Bool = false
    ) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        guard trimmed.utf16.count <= Self.maxNameUTF16Units else { return nil }
        guard amount > .zero else { return nil }
        self.type = type
        self.amount = amount
        self.date = date
        self.name = trimmed
        self.paymentMethod = paymentMethod
        self.card = card
        self.tag = tag
        self.allowDuplicate = allowDuplicate
    }

    private init(copying other: TransactionEntry, allowDuplicate: Bool) {
        type = other.type
        amount = other.amount
        date = other.date
        name = other.name
        paymentMethod = other.paymentMethod
        card = other.card
        tag = other.tag
        self.allowDuplicate = allowDuplicate
    }

    func withAllowingDuplicate() -> TransactionEntry {
        TransactionEntry(copying: self, allowDuplicate: true)
    }
}
