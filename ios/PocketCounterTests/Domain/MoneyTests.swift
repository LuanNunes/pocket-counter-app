import Foundation
import Testing

@testable import PocketCounter

@Suite("Money")
struct MoneyTests {

    @Test("JSON numbers round-trip without drifting", arguments: [
        "1234.56", "0.07", "-0.01", "9999999999999.99", "0.1",
    ])
    func roundTrip(literal: String) throws {
        let json = Data(literal.utf8)

        let decoded = try JSONDecoder().decode(Money.self, from: json)
        let reencoded = try JSONEncoder().encode(decoded)
        let again = try JSONDecoder().decode(Money.self, from: reencoded)

        #expect(decoded.amount == Decimal(string: literal))
        #expect(again == decoded)
    }

    @Test("encoding writes the plain decimal text the backend expects", arguments: [
        "1234.56", "0.07", "-0.01",
    ])
    func encodesPlainNumber(literal: String) throws {
        let money = try JSONDecoder().decode(Money.self, from: Data(literal.utf8))

        let text = String(decoding: try JSONEncoder().encode(money), as: UTF8.self)

        #expect(text == literal)
    }

    @Test("zero is the additive identity")
    func zero() {
        #expect(Money.zero == Money(0))
        #expect(Money(5) + .zero == Money(5))
    }

    @Test("adding and subtracting is exact in decimals")
    func arithmetic() throws {
        let a = try #require(Decimal(string: "0.1"))
        let b = try #require(Decimal(string: "0.2"))
        let c = try #require(Decimal(string: "0.3"))

        #expect(Money(a) + Money(b) == Money(c))
        #expect(Money(c) - Money(b) == Money(a))
    }

    @Test("money orders by amount")
    func ordering() {
        #expect(Money(-1) < Money(0))
        #expect(Money(2) > Money(1))
        #expect([Money(3), Money(1), Money(2)].sorted() == [Money(1), Money(2), Money(3)])
    }

    @Test("absolute value drops the sign")
    func absolute() {
        #expect(Money(-7).abs == Money(7))
        #expect(Money(7).abs == Money(7))
    }

    @Test("a sequence of money sums to its total, empty to zero")
    func sum() {
        #expect([Money(1), Money(2), Money(-0.5)].sum() == Money(2.5))
        #expect([Money]().sum() == .zero)
    }
}
