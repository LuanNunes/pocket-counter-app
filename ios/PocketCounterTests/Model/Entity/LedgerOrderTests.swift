import Testing

@testable import PocketCounter

@Suite("LedgerOrder")
struct LedgerOrderTests {
    private func ids(_ items: [HistoryItem]) -> [String] {
        LedgerOrder.sorted(items).map(\.id.rawValue)
    }

    @Test("months ascend")
    func month() {
        let items = [
            HistoryItem.fixture(id: "nov", date: .of(2026, 11, 1)),
            .fixture(id: "oct", date: .of(2026, 10, 1)),
        ]

        #expect(ids(items) == ["oct", "nov"])
    }

    @Test("incomes come before expenses within a month")
    func kind() {
        let items = [HistoryItem.fixture(id: "e", type: .expense), .fixture(id: "i", type: .income)]

        #expect(ids(items) == ["i", "e"])
    }

    @Test("display order beats date")
    func displayOrder() {
        let items = [
            HistoryItem.fixture(id: "a", date: .of(2026, 10, 1), displayOrder: 2),
            .fixture(id: "b", date: .of(2026, 10, 9), displayOrder: 1),
        ]

        #expect(ids(items) == ["b", "a"])
    }

    @Test("date beats id")
    func date() {
        let items = [
            HistoryItem.fixture(id: "a", date: .of(2026, 10, 9)),
            .fixture(id: "b", date: .of(2026, 10, 1)),
        ]

        #expect(ids(items) == ["b", "a"])
    }

    @Test("rows tied on everything but id come out the same from any input order", arguments: [
        ["a", "b", "c"], ["c", "b", "a"], ["b", "c", "a"],
    ])
    func idBreaksTies(_ input: [String]) {
        #expect(ids(input.map { HistoryItem.fixture(id: $0) }) == ["a", "b", "c"])
    }
}
