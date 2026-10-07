import Foundation
import Testing

@testable import PocketCounter

@Suite("LedgerBoard")
struct LedgerBoardTests {
    private let lookups = LookupSet.fixture(tags: [.fixture("a", "Mercado")])
    private let ref = CalendarDay.fixture.refYearMonth

    private func board(
        _ items: [HistoryItem], kind: TransactionType = .expense, query: String = "",
        onlyFixos: Bool = false, mode: LedgerGroupMode = .lista
    ) -> LedgerBoard {
        LedgerBoard.from(
            MonthLedger(ref: ref, items: items, lookups: lookups),
            filter: LedgerFilter(kind: kind, query: query, onlyFixos: onlyFixos), mode: mode
        )
    }

    @Test("it totals the absolute amounts of the visible rows and counts them")
    func totals() {
        let result = board([.fixture(id: "a", amount: -10), .fixture(id: "b", amount: -5.5), .income(100)])

        #expect(result.total == Money(15.5))
        #expect(result.visibleCount == 2)
        #expect(result.emptiness == .notEmpty)
    }

    @Test("income rows total their amounts and group by title")
    func incomes() {
        let result = board([.fixture(id: "i", amount: 40, type: .income, name: "Salário")], kind: .income, mode: .categoria)

        #expect(result.total == Money(40))
        #expect(result.groups.map(\.identity) == [.incomeName("Salário")])
    }

    @Test("the search and só-fixos narrow the rows before they are grouped and totalled")
    func pipeline() {
        let items = [
            HistoryItem.fixture(id: "a", amount: -10, seriesId: "s", name: "Luz"),
            .fixture(id: "b", amount: -20, seriesId: "s", name: "Água"),
            .fixture(id: "c", amount: -40, name: "Luz"),
            .fixture(id: "d", amount: 70, type: .income, seriesId: "s", name: "Luz"),
        ]

        let result = board(items, query: "luz", onlyFixos: true)

        #expect(result.visibleCount == 1)
        #expect(result.total == Money(10))
        #expect(result.groups.flatMap(\.items).map(\.id.rawValue) == ["a"])
    }

    @Test("a month with no rows of the kind is told apart from a filtered-out one")
    func monthHasNoneOfKind() {
        #expect(board([.income(5)]).emptiness == .monthHasNoneOfKind)
        #expect(board([]).emptiness == .monthHasNoneOfKind)
    }

    @Test("rows of the kind that the search or só-fixos hide are filtered out", arguments: [
        LedgerFilter(kind: .expense, query: "zzz", onlyFixos: false),
        LedgerFilter(kind: .expense, query: "", onlyFixos: true),
    ])
    func filteredOut(_ filter: LedgerFilter) {
        let result = LedgerBoard.from(
            MonthLedger(ref: ref, items: [.fixture(name: "Luz")], lookups: lookups), filter: filter, mode: .lista
        )

        #expect(result.emptiness == .filteredOut)
        #expect(result.visibleCount == 0)
        #expect(result.total == .zero)
        #expect(result.groups == [])
    }
}
