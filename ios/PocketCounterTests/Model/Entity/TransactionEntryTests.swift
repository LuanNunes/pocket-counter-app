import Foundation
import Testing

@testable import PocketCounter

@Suite("TransactionEntry")
struct TransactionEntryTests {
    private func entry(name: String = "Consulta do cachorro", amount: Decimal = 250) -> TransactionEntry? {
        TransactionEntry(type: .expense, amount: Money(amount), date: .fixture, name: name)
    }

    @Test("a complete entry keeps its magnitude and trims its name")
    func complete() throws {
        let entry = try #require(entry(name: "  Consulta do cachorro  "))

        #expect(entry.amount == Money(250))
        #expect(entry.name == "Consulta do cachorro")
        #expect(entry.allowDuplicate == false)
    }

    @Test("a blank name is refused", arguments: ["", " ", "\n\t"])
    func blankName(name: String) {
        #expect(entry(name: name) == nil)
    }

    @Test("an amount that is not a transaction is refused", arguments: [Decimal(0), Decimal(-1)])
    func unusableAmount(amount: Decimal) {
        #expect(entry(amount: amount) == nil)
    }

    /// `Transaction.name` is `length = 250` (`Transaction.kt:39`). Past it the write fails at the
    /// ORM, which is not a clean 400 — so it is refused here, the way `SentenceText` refuses 500.
    @Test("a name past the column's length is refused")
    func overlongName() {
        #expect(entry(name: String(repeating: "a", count: 250)) != nil)
        #expect(entry(name: String(repeating: "a", count: 251)) == nil)
    }

    @Test("the name limit counts UTF-16 units, not characters")
    func multiScalarName() {
        let family = "\u{1F468}\u{200D}\u{1F469}\u{200D}\u{1F467}\u{200D}\u{1F466}"
        #expect(entry(name: String(repeating: family, count: 200)) == nil)
    }

    @Test("the duplicate retry changes only that flag")
    func allowingDuplicate() throws {
        func full(allowDuplicate: Bool) -> TransactionEntry? {
            TransactionEntry(
                type: .expense, amount: Money(250), date: .fixture, name: "Consulta do cachorro",
                paymentMethod: .pix, card: CardID(rawValue: "card-1"), tag: TagID(rawValue: "tag-1"),
                allowDuplicate: allowDuplicate)
        }

        let retry = try #require(full(allowDuplicate: false)).withAllowingDuplicate()

        #expect(retry == full(allowDuplicate: true))
    }
}
