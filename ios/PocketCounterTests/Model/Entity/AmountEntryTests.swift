import Foundation
import Testing

@testable import PocketCounter

@Suite("AmountEntry")
struct AmountEntryTests {
    private func money(_ text: String) -> Money {
        Money(Decimal(string: text, locale: nil) ?? 0)
    }

    @Test("a typed amount in pt-BR notation reads as that amount", arguments: [
        ("150", "150"),
        ("12,5", "12.5"),
        ("12,50", "12.50"),
        ("1.234,56", "1234.56"),
        ("1.234", "1234"),
        ("1.234.567,89", "1234567.89"),
        ("1234.5", "1234.5"),
        ("R$ 1.234,56", "1234.56"),
        ("R$12,5", "12.5"),
        ("  0,07 ", "0.07"),
        ("12.50", "12.50"),
        ("12.5", "12.5"),
        ("1.25", "1.25"),
        ("1.250", "1250"),
        ("0.5", "0.5"),
        ("R$ 12.50", "12.50"),
    ])
    func reads(typed: String, expected: String) {
        #expect(AmountEntry.money(typed) == money(expected))
    }

    @Test("the currency prefix is read in any case", arguments: ["r$ 50", "R$ 50", "r$50"])
    func prefixCase(typed: String) {
        #expect(AmountEntry.money(typed) == money("50"))
    }

    @Test("a trailing dot or a fourth-digit group is read for what it is", arguments: [("5.", nil), ("1234.567", nil), (".", nil)] as [(String, String?)])
    func dotEdges(typed: String, expected: String?) {
        #expect(AmountEntry.money(typed) == expected.map(money))
    }

    @Test("the sum of typed amounts is exact, not a binary float's")
    func exact() throws {
        let sum = try #require(AmountEntry.money("1,10")) + #require(AmountEntry.money("2,20"))

        #expect(sum == money("3.30"))
    }

    @Test("a non-positive amount is refused", arguments: ["0", "0,00", "-5", "-0,01", "R$ 0"])
    func nonPositive(typed: String) {
        #expect(AmountEntry.money(typed) == nil)
    }

    @Test("a third fraction digit is refused rather than rounded", arguments: ["1,234", "0,001", "10,999"])
    func overPrecise(typed: String) {
        #expect(AmountEntry.money(typed) == nil)
    }

    @Test("ambiguous or malformed separators are refused", arguments: [
        "1,2,3", "1,234,567", "1.23.4", "1,234.56", "1.2345", "1.2.50", ".5", ",5", "5,", "1..000", "", "R$", "abc", "12a", "1e3",
    ])
    func malformed(typed: String) {
        #expect(AmountEntry.money(typed) == nil)
    }
}
