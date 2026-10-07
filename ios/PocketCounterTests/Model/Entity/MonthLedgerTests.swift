import Testing

@testable import PocketCounter

@Suite("MonthLedger applying")
struct MonthLedgerApplyingTests {
    private func ledger(_ items: HistoryItem...) -> MonthLedger {
        MonthLedger(ref: CalendarDay.fixture.refYearMonth, items: items, lookups: .fixture())
    }

    private let a = HistoryItem.fixture(id: "a", amount: -10, statusPayment: .pending)
    private let b = HistoryItem.fixture(id: "b", amount: -20, statusPayment: .pending)

    @Test("an empty map returns the ledger as it was")
    func empty() {
        let ledger = ledger(a, b)

        #expect(ledger.applying([:]) == ledger)
    }

    @Test("only the named items change")
    func named() {
        let applied = ledger(a, b).applying([a.id: .paid])

        #expect(applied == ledger(a.settingPaymentStatus(.paid), b))
    }

    @Test("an id the ledger does not hold is ignored")
    func absent() {
        let ledger = ledger(a, b)

        #expect(ledger.applying([TransactionID(rawValue: "gone"): .paid]) == ledger)
    }

    @Test("a target equal to the current status changes nothing")
    func same() {
        let ledger = ledger(a, b)

        #expect(ledger.applying([a.id: .pending]) == ledger)
    }

    @Test("the pending total moves by exactly the toggled row's magnitude")
    func pendingTotal() {
        let ledger = ledger(a, b)

        let applied = ledger.applying([b.id: .paid])

        #expect(applied.kpis.pendingTotal == Money(10))
        #expect(applied.kpis.pendingCount == 1)
        #expect(ledger.kpis.pendingTotal == Money(30))
    }
}

@Suite("MonthLedger removing")
struct MonthLedgerRemovingTests {
    private let lookups = LookupSet.fixture(tags: [.fixture("g1")])
    private let a = HistoryItem.fixture(id: "a", amount: -10, statusPayment: .pending)
    private let b = HistoryItem.fixture(id: "b", amount: -20, statusPayment: .pending)
    private let c = HistoryItem.fixture(id: "c", amount: -30, statusPayment: .pending)

    private func ledger(_ items: HistoryItem...) -> MonthLedger {
        MonthLedger(ref: CalendarDay.fixture.refYearMonth, items: items, lookups: lookups)
    }

    @Test("the named item leaves; order, ref and lookups stay")
    func removes() {
        #expect(ledger(a, b, c).removing(b.id) == ledger(a, c))
    }

    @Test("an id the ledger does not hold changes nothing")
    func absent() {
        let ledger = ledger(a, b)

        #expect(ledger.removing(TransactionID(rawValue: "gone")) == ledger)
    }

    @Test("the totals no longer count the row")
    func totals() {
        #expect(ledger(a, b).removing(a.id).kpis.pendingTotal == Money(20))
    }
}

@Suite("MonthLedger reordering")
struct MonthLedgerReorderingTests {
    private func ledger(_ items: [HistoryItem]) -> MonthLedger {
        MonthLedger(ref: CalendarDay.fixture.refYearMonth, items: items, lookups: .fixture())
    }

    private func expenseRow(_ id: String, order: Int = 0) -> HistoryItem {
        .fixture(id: id, amount: -10, type: .expense, displayOrder: order)
    }

    private func incomeRow(_ id: String, order: Int = 0) -> HistoryItem {
        .fixture(id: id, amount: 10, type: .income, displayOrder: order)
    }

    private func ids(_ raws: String...) -> [TransactionID] {
        raws.map { TransactionID(rawValue: $0) }
    }

    @Test("the kind reads back in exactly the order given, with dense display orders")
    func oneRule() {
        let items = [expenseRow("a"), expenseRow("b"), expenseRow("c"), expenseRow("d")]
        let order = ids("c", "a", "d", "b")

        let reordered = ledger(items).reordering(order)

        let expenses = reordered.items.filter { $0.type == .expense }
        #expect(expenses.map(\.id) == order)
        #expect(expenses.map(\.displayOrder) == [0, 1, 2, 3])
    }

    @Test("stale display orders are overwritten, not merged with the new ones")
    func staleOrders() {
        let items = [expenseRow("a", order: 5), expenseRow("b", order: 2), expenseRow("c", order: 9)]
        let order = ids("b", "c", "a")

        let expenses = ledger(items).reordering(order).items.filter { $0.type == .expense }

        #expect(expenses.map(\.id) == order)
        #expect(expenses.map(\.displayOrder) == [0, 1, 2])
    }

    @Test("rows of the other kind keep their display order and their relative order")
    func otherKindUntouched() {
        let incomes = [incomeRow("i1", order: 7), incomeRow("i2", order: 3)]
        let items = incomes + [expenseRow("a"), expenseRow("b")]

        let reordered = ledger(items).reordering(ids("b", "a"))

        #expect(reordered.items.filter { $0.type == .income } == [incomes[1], incomes[0]])
        #expect(reordered.items.count == 4)
    }

    @Test("a row the order does not name survives with its own display order")
    func unnamedSurvives() {
        let kept = expenseRow("z", order: 4)
        let items = [expenseRow("a"), expenseRow("b"), kept]

        let reordered = ledger(items).reordering(ids("b", "a"))

        #expect(reordered.items.contains(kept))
        #expect(reordered.items.count == 3)
    }
}
