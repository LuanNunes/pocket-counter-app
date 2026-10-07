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

    private let target = DetailTarget(id: TransactionID(rawValue: "t"), ref: .current)

    @Test("opening a detail remembers the row and closing forgets it")
    func detail() {
        var state = TransactionsViewState()
        #expect(state.detail == nil)

        state.openDetail(target)
        #expect(state.detail == target)

        state.closeDetail()
        #expect(state.detail == nil)
    }

    @Test("a different month closes the detail")
    func monthClosesDetail() {
        var state = TransactionsViewState()
        state.openDetail(target)

        state.monthChanged()

        #expect(state.detail == nil)
    }

    @Test("a kind, mode or query change keeps the detail: a filter is a lens, the row is still in the month")
    func filtersKeepDetail() {
        var state = TransactionsViewState()
        state.openDetail(target)

        state.select(kind: .income)
        state.select(mode: .categoria)
        state.set(query: "luz")
        state.toggleOnlyFixos()

        #expect(state.detail == target)
    }

    @Test("beginning to reorder clears só fixos and the collapse, which would hide rows from the order")
    func beginReordering() {
        var state = TransactionsViewState(onlyFixos: true)
        state.toggle(group)

        state.beginReordering()

        #expect(state.isReordering)
        #expect(!state.onlyFixos)
        #expect(state.collapsed.isEmpty)
    }

    @Test("ending a reorder leaves the screen as it was")
    func endReordering() {
        var state = TransactionsViewState()
        state.beginReordering()

        state.endReordering()

        #expect(!state.isReordering)
    }

    @Test("a query forbids reordering, and beginning is then a no-op", arguments: ["luz", " luz "])
    func queryForbids(query: String) {
        var state = TransactionsViewState(query: query, onlyFixos: true)

        state.beginReordering()

        #expect(!state.canReorder)
        #expect(!state.isReordering)
        #expect(state.onlyFixos)
    }

    @Test("a blank query still allows reordering", arguments: ["", "   "])
    func blankQueryAllows(query: String) {
        var state = TransactionsViewState(query: query)

        state.beginReordering()

        #expect(state.canReorder)
        #expect(state.isReordering)
    }

    @Test("a month change ends reordering")
    func monthEndsReordering() {
        var state = TransactionsViewState()
        state.beginReordering()

        state.monthChanged()

        #expect(!state.isReordering)
    }

    @Test("a kind or mode change keeps reordering")
    func selectionKeepsReordering() {
        var state = TransactionsViewState()
        state.beginReordering()

        state.select(kind: .income)
        state.select(mode: .tag)

        #expect(state.isReordering)
    }
}

@Suite("TransactionsViewState, reordenação")
struct TransactionsViewStateReorderTests {
    private func reordering() -> TransactionsViewState {
        var state = TransactionsViewState()
        state.beginReordering()
        return state
    }

    @Test("a busca digitada durante a reordenação a encerra")
    func queryEndsReordering() {
        var state = reordering()

        state.set(query: "luz")

        #expect(!state.isReordering)
    }

    @Test("limpar a busca não reabre a reordenação")
    func clearingQueryDoesNotResume() {
        var state = reordering()
        state.set(query: "luz")

        state.set(query: "")

        #expect(!state.isReordering)
    }
}
