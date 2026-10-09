import Foundation
import Testing

@testable import PocketCounter

@Suite("LedgerFilter")
struct LedgerFilterTests {
    private let lookups = LookupSet.fixture(tags: [.fixture("a", "Mercado"), .fixture("b", "Açaí")])

    private func ids(
        _ items: [HistoryItem], kind: TransactionType = .expense, query: String = "", onlyFixos: Bool = false
    ) -> [String] {
        LedgerFilter(kind: kind, query: query, onlyFixos: onlyFixos)
            .apply(to: items, lookups: lookups).map(\.id.rawValue)
    }

    @Test("only rows of the chosen kind pass")
    func kind() {
        let items = [HistoryItem.fixture(id: "e", type: .expense), .fixture(id: "i", type: .income)]

        #expect(ids(items, kind: .income) == ["i"])
    }

    @Test("a blank or whitespace query matches everything", arguments: ["", "   ", "\n"])
    func blank(_ query: String) {
        #expect(ids([.fixture(id: "a", name: "Luz")], query: query) == ["a"])
    }

    @Test("the title matches ignoring case and accents")
    func title() {
        let items = [HistoryItem.fixture(id: "a", name: "Pão de Açúcar"), .fixture(id: "b", name: "Luz")]

        #expect(ids(items, query: "acucar") == ["a"])
        #expect(ids(items, query: "PAO") == ["a"])
    }

    @Test("the description stands in when there is no name")
    func description() {
        #expect(ids([.fixture(id: "a", description: "Feira")], query: "feira") == ["a"])
    }

    @Test("an effective tag's name matches, ignoring accents")
    func tagName() {
        let items = [
            HistoryItem.fixture(id: "a", tagIds: [.of("b")], name: "Lanche"),
            .fixture(id: "b", tagIds: [.of("a")], name: "Lanche"),
        ]

        #expect(ids(items, query: "acai") == ["a"])
    }

    @Test("any of several tags matches")
    func anyTag() {
        #expect(ids([.fixture(id: "a", tagIds: [.of("a"), .of("b")])], query: "acai") == ["a"])
    }

    @Test("an explicit empty tag list matches no tag, and nil inherits nothing yet")
    func tagStates() {
        let items = [HistoryItem.fixture(id: "a", tagIds: []), .fixture(id: "b", tagIds: nil)]

        #expect(ids(items, query: "mercado") == [])
    }

    @Test("a deleted tag matches nothing")
    func deletedTag() {
        #expect(ids([.fixture(id: "a", tagIds: [.of("gone")])], query: "gone") == [])
    }

    @Test("the digits of the amount match, whatever the sign or separators", arguments: ["123456", "1.234,56", "R$ 1234", "56"])
    func digits(_ query: String) {
        #expect(ids([.fixture(id: "a", amount: -1234.56, name: "x")], query: query) == ["a"])
    }

    @Test("amounts compare with two decimals, so 10.5 reads as 1050")
    func twoDecimals() {
        #expect(ids([.fixture(id: "a", amount: -10.5, name: "x")], query: "10,50") == ["a"])
    }

    @Test("a query without digits never matches by amount")
    func noDigits() {
        #expect(ids([.fixture(id: "a", amount: -1234.56, name: "x")], query: "R$ ,") == [])
    }

    @Test("a different amount does not match")
    func otherAmount() {
        #expect(ids([.fixture(id: "a", amount: -99, name: "x")], query: "100") == [])
    }

    @Test("only fixos keeps rows that belong to a recurring transaction")
    func onlyFixos() {
        let items = [HistoryItem.fixture(id: "f", recurringTransactionId: "s"), .fixture(id: "n")]

        #expect(ids(items, onlyFixos: true) == ["f"])
        #expect(ids(items, onlyFixos: false) == ["f", "n"])
    }
}
