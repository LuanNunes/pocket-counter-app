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

    @Test("a name the server would refuse is refused here", arguments: ["", " ", "\n\t"])
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

    @Test("the duplicate retry changes only that flag")
    func allowingDuplicate() throws {
        let retry = try #require(entry()).withAllowingDuplicate()

        #expect(retry.allowDuplicate)
        #expect(retry.amount == Money(250))
        #expect(retry.name == "Consulta do cachorro")
    }
}
