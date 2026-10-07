import Testing

@testable import PocketCounter

@Suite("TransactionsViewState")
struct TransactionsViewStateTests {
    private let group = LedgerGroupIdentity.tag(.of("a"))

    @Test("toggling a group collapses it and toggling again expands it")
    func toggle() {
        var state = TransactionsViewState()
        state.toggle(group)
        #expect(state.collapsed == [group])
        state.toggle(group)
        #expect(state.collapsed.isEmpty)
    }

    @Test("a different mode, kind or month forgets what was collapsed")
    func resets() {
        var state = TransactionsViewState()
        state.toggle(group)
        state.select(mode: .tag)
        #expect(state.collapsed.isEmpty)
        state.toggle(group)
        state.select(kind: .income)
        #expect(state.collapsed.isEmpty)
        state.toggle(group)
        state.monthChanged()
        #expect(state.collapsed.isEmpty)
    }

    @Test("selecting what is already selected keeps the collapse")
    func sameSelection() {
        var state = TransactionsViewState()
        state.toggle(group)
        state.select(mode: .lista)
        state.select(kind: .expense)
        #expect(state.collapsed == [group])
    }

    @Test("the filter follows kind, query and só fixos")
    func filter() {
        var state = TransactionsViewState()
        state.select(kind: .income)
        state.set(query: "luz")
        state.toggleOnlyFixos()
        #expect(state.filter == LedgerFilter(kind: .income, query: "luz", onlyFixos: true))
        state.toggleOnlyFixos()
        #expect(!state.onlyFixos)
    }

    @Test("a query or só fixos survives a mode change and keeps the collapse")
    func filterKeepsCollapse() {
        var state = TransactionsViewState()
        state.toggle(group)
        state.set(query: "a")
        state.toggleOnlyFixos()
        #expect(state.collapsed == [group])
        state.select(mode: .categoria)
        #expect(state.query == "a" && state.onlyFixos)
    }

    @Test("a query that matches nothing reaches the filtered-out state")
    func queryReachesFilteredOut() throws {
        var state = TransactionsViewState()
        state.set(query: "zzz")
        let ref = try RefYearMonth(year: 2026, month: 10)
        let ledger = MonthLedger(ref: ref, items: [.fixture(name: "Luz")], lookups: LookupSet(categories: [], tags: [], cards: [], failed: []))
        #expect(LedgerBoard.from(ledger, filter: state.filter, mode: state.mode).emptiness == .filteredOut)
    }
}
