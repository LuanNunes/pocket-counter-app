import Testing

@testable import PocketCounter

private let october = RefYearMonth(raw: 202610)!
private let november = RefYearMonth(raw: 202611)!

private let rent = HistoryItem.fixture(id: "rent", amount: -10, statusPayment: .pending, displayOrder: 0)
private let gym = HistoryItem.fixture(id: "gym", amount: -20, statusPayment: .pending, displayOrder: 1)
private let market = HistoryItem.fixture(id: "market", amount: -30, statusPayment: .pending, displayOrder: 2)
private let salary = HistoryItem.fixture(id: "salary", amount: 100, type: .income, displayOrder: 0)
private let bonus = HistoryItem.fixture(id: "bonus", amount: 50, type: .income, displayOrder: 1)

private func ledger(_ ref: RefYearMonth = october) -> MonthLedger {
    MonthLedger(ref: ref, items: [salary, bonus, rent, gym, market], lookups: .fixture())
}

private func ids(_ items: HistoryItem...) -> [TransactionID] {
    items.map(\.id)
}

private func order(of kind: TransactionType, in ledger: MonthLedger) -> [TransactionID] {
    ledger.items.filter { $0.type == kind }.map(\.id)
}

private let expenses = ReorderKey(ref: october, kind: .expense)
private let incomes = ReorderKey(ref: october, kind: .income)

@Suite("LedgerWrites")
struct LedgerWritesTests {
    private func write(_ phase: PaymentStatusWrite.Phase) -> PaymentStatusWrite {
        PaymentStatusWrite(ref: .current, phase: phase)
    }

    private func failedEverywhere() -> LedgerWrites {
        var writes = LedgerWrites()
        writes.failStatus(rent.id, ref: october, .server)
        writes.beginIntent(gym.id, ref: october, target: .fixo(true))
        writes.failIntent(gym.id, ref: october, .server)
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        writes.failReorder(expenses, .server)
        return writes
    }

    @Test("an answer older than a failed write does not retire it")
    func olderAnswerKeepsFailures() {
        var writes = failedEverywhere()

        writes.answered(for: october, at: 0)

        #expect(writes == failedEverywhere())
    }

    @Test("an answer issued after the failures retires them")
    func newerAnswerRetiresFailures() {
        var writes = failedEverywhere()

        writes.answered(for: october, at: writes.revision)

        #expect(writes.statuses.isEmpty)
        #expect(writes.intents.isEmpty)
        #expect(writes.reorderFailure(in: october, kind: .expense) == nil)
    }

    @Test("an answer older than a settled deletion keeps the row hidden")
    func olderAnswerKeepsDeletion() {
        var writes = LedgerWrites()
        writes.beginIntent(rent.id, ref: october, target: .deletion)
        let issued = writes.revision
        writes.settleDeletion(rent.id, ref: october)

        writes.answered(for: october, at: issued)

        #expect(writes.overlaying(ledger()).items.map(\.id) == ids(salary, bonus, gym, market))
        #expect(writes.intents[rent.id] == RowIntentWrite(ref: october, phase: .settled(.deletion)))
    }

    @Test("an answer older than a settled status keeps the new status")
    func olderAnswerKeepsStatus() {
        var writes = LedgerWrites()
        writes.beginStatus(rent.id, ref: october, target: .paid)
        let issued = writes.revision
        writes.settleStatus(rent.id, ref: october, target: .paid)

        writes.answered(for: october, at: issued)

        #expect(writes.overlaying(ledger()).items.first { $0.id == rent.id }?.statusPayment == .paid)
    }

    @Test("an answer issued after a settled status retires it")
    func newerAnswerRetiresSettled() {
        var writes = LedgerWrites()
        writes.settleStatus(rent.id, ref: october, target: .paid)
        writes.settleDeletion(gym.id, ref: october)
        writes.recordReorder(expenses, order: ids(market, rent))
        writes.settleReorder(expenses)

        writes.answered(for: october, at: writes.revision)

        #expect(writes.statuses.isEmpty)
        #expect(writes.intents.isEmpty)
        #expect(writes.overlaying(ledger()) == ledger())
    }

    @Test("an in-flight reorder survives any answer and projects onto it, needing no age")
    func inFlightReorderSurvives() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        #expect(writes.revision == 0)

        writes.answered(for: october, at: 0)
        writes.answered(for: october, at: .max)

        #expect(order(of: .expense, in: writes.overlaying(ledger())) == ids(market, gym, rent))
        #expect(order(of: .income, in: writes.overlaying(ledger())) == ids(salary, bonus))
    }

    @Test("an answer issued after a settled reorder retires it")
    func newerAnswerRetiresReorder() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        writes.settleReorder(expenses)
        #expect(order(of: .expense, in: writes.overlaying(ledger())) == ids(market, gym, rent))

        writes.answered(for: october, at: writes.revision)

        #expect(order(of: .expense, in: writes.overlaying(ledger())) == ids(rent, gym, market))
    }

    @Test("an answer older than a settled reorder keeps it")
    func olderAnswerKeepsReorder() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        let issued = writes.revision
        writes.settleReorder(expenses)

        writes.answered(for: october, at: issued)

        #expect(order(of: .expense, in: writes.overlaying(ledger())) == ids(market, gym, rent))
    }

    @Test("an answer for another month retires nothing")
    func otherMonthRetiresNothing() {
        var writes = failedEverywhere()

        writes.answered(for: november, at: writes.revision)

        #expect(writes == failedEverywhere())
    }

    @Test("a month holds facts newer than an answer only when one was recorded after it was issued")
    func holdsFacts() {
        var writes = LedgerWrites()
        #expect(!writes.holdsFacts(newerThan: 0, in: october))

        writes.failStatus(rent.id, ref: october, .server)

        #expect(writes.holdsFacts(newerThan: 0, in: october))
        #expect(!writes.holdsFacts(newerThan: writes.revision, in: october))
        #expect(!writes.holdsFacts(newerThan: 0, in: november))
    }

    @Test("one fact newer than the answer shields the whole month, including the facts the answer covers")
    func coarseRetirement() {
        var writes = LedgerWrites()
        writes.failStatus(rent.id, ref: october, .server)
        let issued = writes.revision
        writes.failStatus(gym.id, ref: october, .server)

        writes.answered(for: october, at: issued)

        #expect(writes.statuses[rent.id]?.phase == .failed(.server))
        #expect(writes.statuses[gym.id]?.phase == .failed(.server))
    }

    @Test("an in-flight write is retired by no answer, however new")
    func inFlightNeverRetired() {
        var writes = LedgerWrites()
        writes.beginStatus(rent.id, ref: october, target: .paid)
        writes.beginIntent(gym.id, ref: october, target: .deletion)

        writes.answered(for: october, at: .max)

        #expect(writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .inFlight(.paid)))
        #expect(writes.intents[gym.id] == RowIntentWrite(ref: october, phase: .inFlight(.deletion)))
        #expect(writes.revision == 0)
    }

    @Test("beginning a write is no fact; settling and failing are")
    func revisionAdvance() {
        var writes = LedgerWrites()
        writes.beginStatus(rent.id, ref: october, target: .paid)
        writes.beginIntent(gym.id, ref: october, target: .deletion)
        writes.recordReorder(expenses, order: ids(market, rent))
        writes.dropStatus(rent.id)
        writes.dropIntent(gym.id)
        writes.dropReorder(expenses)
        #expect(writes.revision == 0)

        let verbs: [(inout LedgerWrites) -> Void] = [
            { $0.settleStatus(rent.id, ref: october, target: .paid) },
            { $0.failStatus(rent.id, ref: october, .server) },
            { $0.settleDeletion(rent.id, ref: october) },
            { $0.failIntent(rent.id, ref: october, .server) },
            { $0.recordReorder(expenses, order: []); $0.settleReorder(expenses) },
            { $0.recordReorder(expenses, order: []); $0.failReorder(expenses, .server) },
        ]
        for verb in verbs {
            let before = writes.revision
            verb(&writes)
            #expect(writes.revision == before + 1)
        }
    }

    @Test("two reorders touch disjoint rows, so the overlay equals applying them in either order")
    func bothKinds() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        writes.recordReorder(incomes, order: ids(bonus, salary))
        let expensesFirst = ledger().reordering(ids(market, gym, rent)).reordering(ids(bonus, salary))
        let incomesFirst = ledger().reordering(ids(bonus, salary)).reordering(ids(market, gym, rent))

        let shown = writes.overlaying(ledger())

        #expect(shown == expensesFirst)
        #expect(shown == incomesFirst)
        #expect(order(of: .expense, in: shown) == ids(market, gym, rent))
        #expect(order(of: .income, in: shown) == ids(bonus, salary))
    }

    @Test("a reorder that names a deleted row still orders the remaining rows")
    func reorderNamingDeletedRow() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(rent, market, gym))
        writes.settleDeletion(rent.id, ref: october)

        let shown = writes.overlaying(ledger())

        #expect(order(of: .expense, in: shown) == ids(market, gym))
    }

    @Test("a row the order does not name keeps its own displayOrder and falls into a slot the user never chose")
    func unnamedRowTies() {
        let fresh = HistoryItem.fixture(id: "fresh", amount: -5, statusPayment: .pending, displayOrder: 0)
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        let answer = MonthLedger(ref: october, items: [salary, bonus, rent, gym, market, fresh], lookups: .fixture())

        let shown = writes.overlaying(answer)

        #expect(shown.items.first { $0.id == fresh.id }?.displayOrder == 0)
        #expect(order(of: .expense, in: shown) == ids(fresh, market, gym, rent))
    }

    @Test("a deletion also clears a failed status write on that row")
    func deletionClearsStatus() {
        var writes = LedgerWrites()
        writes.failStatus(rent.id, ref: october, .server)

        writes.settleDeletion(rent.id, ref: october)

        #expect(writes.statuses.isEmpty)
    }

    @Test("writes of another month never project onto this month's ledger")
    func overlayScopedByMonth() {
        var writes = LedgerWrites()
        writes.settleStatus(rent.id, ref: november, target: .paid)
        writes.settleDeletion(gym.id, ref: november)

        #expect(writes.overlaying(ledger()) == ledger())
    }

    @Test("the reorder registry is keyed by month and kind, and only a failed entry reports a failure")
    func reorderFailureScoping() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, rent))
        #expect(writes.reorderFailure(in: october, kind: .expense) == nil)
        writes.settleReorder(expenses)
        #expect(writes.reorderFailure(in: october, kind: .expense) == nil)

        writes.failReorder(expenses, .unreachable)

        #expect(writes.reorderFailure(in: october, kind: .expense) == .unreachable)
        #expect(writes.reorderFailure(in: october, kind: .income) == nil)
        #expect(writes.reorderFailure(in: november, kind: .expense) == nil)
    }

    /// Two drags share one entry, so the second's answer arrives for the first's failure.
    @Test("another drag's success does not settle a failure, and the notice stands")
    func settleLeavesFailureAlone() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))
        writes.failReorder(expenses, .server)

        writes.settleReorder(expenses)

        #expect(writes.reorderFailure(in: october, kind: .expense) == .server)
    }

    @Test("a fresh drag replaces the failure and projects its order again")
    func recordSupersedesFailure() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, rent))
        writes.failReorder(expenses, .server)

        writes.recordReorder(expenses, order: ids(gym, rent))

        #expect(writes.reorderFailure(in: october, kind: .expense) == nil)
        #expect(order(of: .expense, in: writes.overlaying(ledger())) == ids(gym, rent, market))
    }

    @Test("dropping a reorder reverts the projection and forgets the failure")
    func dropReorder() {
        var writes = LedgerWrites()
        writes.recordReorder(expenses, order: ids(market, gym, rent))

        writes.dropReorder(expenses)

        #expect(writes.overlaying(ledger()) == ledger())
    }

    @Test("a write that settled is announced")
    func completed() {
        let a = TransactionID(rawValue: "a")
        let b = TransactionID(rawValue: "b")
        let old = [a: write(.inFlight(.paid)), b: write(.inFlight(.pending))]
        let new = [a: write(.settled(.paid)), b: write(.failed(.server))]

        #expect(LedgerWrites.completed(from: old, to: new) == [.paid])
    }

    /// `.sessionExpired` drops the write without recording a failure, and nothing was saved.
    @Test("a write dropped from the registry is not announced")
    func droppedIsNotCompleted() {
        let a = TransactionID(rawValue: "a")

        #expect(LedgerWrites.completed(from: [a: write(.inFlight(.paid))], to: [:]).isEmpty)
    }

    @Test("a settled write retired by an answer is not announced")
    func retiredIsNotCompleted() {
        let a = TransactionID(rawValue: "a")

        #expect(LedgerWrites.completed(from: [a: write(.settled(.paid))], to: [:]).isEmpty)
    }
}
