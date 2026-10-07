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
