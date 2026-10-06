import Foundation

/// BRL amount. The app is single-currency and the backend sends no currency code,
/// so there is deliberately no `Currency` here.
struct Money: Codable, Hashable, Comparable, Sendable {
    static let zero = Money(0)

    let amount: Decimal

    var abs: Money {
        Money(Swift.abs(amount))
    }

    init(_ amount: Decimal) {
        self.amount = amount
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        amount = try container.decode(Decimal.self)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(amount)
    }

    static func + (lhs: Money, rhs: Money) -> Money {
        Money(lhs.amount + rhs.amount)
    }

    static func - (lhs: Money, rhs: Money) -> Money {
        Money(lhs.amount - rhs.amount)
    }

    static func < (lhs: Money, rhs: Money) -> Bool {
        lhs.amount < rhs.amount
    }
}

extension Sequence where Element == Money {
    func sum() -> Money {
        reduce(.zero, +)
    }
}
