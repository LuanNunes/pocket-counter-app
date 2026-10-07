import Testing

@testable import PocketCounter

private let october = RefYearMonth(raw: 202610)!
private let november = RefYearMonth(raw: 202611)!

private let rent = HistoryItem.fixture(id: "rent", amount: -10, displayOrder: 0)
private let gym = HistoryItem.fixture(id: "gym", amount: -20, displayOrder: 1)
private let market = HistoryItem.fixture(id: "market", amount: -30, displayOrder: 2)
private let salary = HistoryItem.fixture(id: "salary", amount: 100, type: .income, displayOrder: 0)
private let bonus = HistoryItem.fixture(id: "bonus", amount: 50, type: .income, displayOrder: 1)

private func ids(_ items: HistoryItem...) -> [TransactionID] {
    items.map(\.id)
}

private func ledger(_ ref: RefYearMonth = october, _ items: [HistoryItem] = [salary, bonus, rent, gym, market]) -> MonthLedger {
    MonthLedger(ref: ref, items: items, lookups: .fixture())
}

@MainActor private func expenses(_ model: MonthLedgerModel) -> [HistoryItem] {
    model.state.load.value?.items.filter { $0.type == .expense } ?? []
}

@MainActor
private struct Harness {
    let source = LedgerSourceFake()
    let expiry = ExpirySignal()
    let status: WriteProbe<TransactionID>
    let reorder: WriteProbe<[TransactionID]>

    init(status: WriteProbe<TransactionID> = .init(), reorder: WriteProbe<[TransactionID]> = .init()) {
        self.status = status
        self.reorder = reorder
    }

    func model(loaded: Bool = true) async -> MonthLedgerModel {
        source.ledgers = [october: ledger(), november: ledger(november, [])]
        let status = status, reorder = reorder
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action,
            setPaymentStatus: { id, _ throws(WriteFailure) in try await status.run(id) },
            reorderTransactions: { ids throws(WriteFailure) in try await reorder.run(ids) },
            onSessionExpired: expiry.action
        )
        if loaded { await model.load() }
        return model
    }
}

@MainActor
@Suite("MonthLedgerModel reordering")
struct MonthLedgerReorderTests {
    @Test("a drag sends the kind's full order once, and no id of the other kind")
    func sendsKindOrder() async {
        let harness = Harness()
        let model = await harness.model()

        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(harness.reorder.calls == [ids(market, gym, rent)])
    }

    @Test("the committed ledger shows the new order at once, dense within the kind, and the other kind is untouched")
    func optimistic() async {
        let harness = Harness(reorder: .init(rendezvous: Rendezvous()))
        let model = await harness.model()
        let task = Task { await model.reorder(ids(market, rent), of: .expense, in: october) }
        await harness.reorder.untilCalled()

        #expect(expenses(model).map(\.id) == ids(market, gym, rent))
        #expect(expenses(model).map(\.displayOrder) == [0, 1, 2])
        #expect(model.state.load.value?.items.filter { $0.type == .income } == [salary, bonus])
        task.cancel()
    }

    @Test("a success reloads nothing and records nothing")
    func success() async {
        let harness = Harness()
        let model = await harness.model()

        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(harness.source.calls == [october])
        #expect(model.state.writes.failedReorder == nil)
        #expect(expenses(model).map(\.id) == ids(market, gym, rent))
        #expect(harness.expiry.count == 0)
    }

    @Test("an order that changes nothing sends nothing")
    func identity() async {
        let harness = Harness()
        let model = await harness.model()
        let before = model.state

        await model.reorder(ids(rent, gym, market), of: .expense, in: october)

        #expect(harness.reorder.calls.isEmpty)
        #expect(model.state == before)
    }

    @Test("a month the model holds no value for sends nothing")
    func unloaded() async {
        let harness = Harness()
        let unloaded = await harness.model(loaded: false)

        await unloaded.reorder(ids(market, rent), of: .expense, in: october)
        await unloaded.reorder(ids(market, rent), of: .expense, in: november)

        #expect(harness.reorder.calls.isEmpty)
    }

    @Test("a failure records the reorder, reloads once and shows what the server holds", arguments: [
        WriteFailure.unreachable, .server, .authenticationUnavailable, .rejected("Não pode"), .vanished,
    ])
    func failure(failure: WriteFailure) async {
        let harness = Harness(reorder: .init(.failure(failure)))
        let model = await harness.model()

        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(model.state.writes.failedReorder == FailedReorder(ref: october, kind: .expense, failure: failure))
        #expect(harness.source.calls == [october, october])
        #expect(model.state.load.phase == .loaded(ledger()))
        #expect(harness.expiry.count == 0)
    }

    @Test("a later drag that succeeds clears the notice the previous one left")
    func retrySupersedes() async {
        let harness = Harness(reorder: .init(.failure(.server)))
        let model = await harness.model()
        await model.reorder(ids(market, rent), of: .expense, in: october)
        #expect(model.state.writes.failedReorder != nil)

        harness.reorder.answer(.success(()))
        // The failed attempt reloaded, so the committed order is the server's again: repeat the move.
        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(model.state.writes.failedReorder == nil)
    }

    @Test("a failure whose reload also fails leaves the month stale and the notice standing")
    func reloadFails() async {
        let harness = Harness(reorder: .init(.failure(.server)))
        let model = await harness.model()
        harness.source.failure = .unreachable

        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(model.state.load.phase == .stale(ledger().reordering(ids(market, gym, rent)), .unreachable))
        #expect(model.state.writes.failedReorder == FailedReorder(ref: october, kind: .expense, failure: .server))
    }

    @Test("a failure after the month changed records for its own month and reloads nothing")
    func monthChanged() async {
        let hold = Rendezvous()
        let harness = Harness(reorder: .init(.failure(.server), rendezvous: hold))
        let model = await harness.model()
        let task = Task { await model.reorder(ids(market, rent), of: .expense, in: october) }
        await hold.untilArrivals(1)
        model.select(november)
        await model.load()

        await hold.release()
        await task.value

        #expect(model.state.writes.failedReorder == FailedReorder(ref: october, kind: .expense, failure: .server))
        #expect(harness.source.calls == [october, november])
        model.select(october)
        #expect(expenses(model).map(\.id) == ids(rent, gym, market))
        #expect(expenses(model).map(\.displayOrder) == [0, 1, 2])
    }

    @Test("an expired session signals once, records nothing and reloads nothing")
    func sessionExpired() async {
        let harness = Harness(reorder: .init(.failure(.sessionExpired)))
        let model = await harness.model()

        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(harness.expiry.count == 1)
        #expect(model.state.writes.failedReorder == nil)
        #expect(harness.source.calls == [october])
    }

    @Test("a month answering clears the failed reorder of that month only")
    func commitClearsOwnMonth() async {
        let harness = Harness(reorder: .init(.failure(.server)))
        let model = await harness.model()
        harness.source.failure = .unreachable
        await model.reorder(ids(market, rent), of: .expense, in: october)
        harness.source.failure = nil
        let failed = model.state.writes.failedReorder

        model.select(november)
        await model.load()
        #expect(model.state.writes.failedReorder == failed)

        model.select(october)
        await model.refresh()
        #expect(model.state.writes.failedReorder == nil)
    }

    @Test("a reorder never closes or opens the door of a row")
    func noDoor() async {
        let hold = Rendezvous()
        let harness = Harness(reorder: .init(rendezvous: hold))
        let model = await harness.model()
        let task = Task { await model.reorder(ids(market, rent), of: .expense, in: october) }
        await hold.untilArrivals(1)

        #expect(!model.state.writes.isWriting(rent.id))
        await model.togglePaymentStatus(of: rent)

        #expect(harness.status.calls == [rent.id])
        await hold.release()
        await task.value
    }

    @Test("a reorder goes through while a status write on the same row is in flight")
    func reorderDuringStatusWrite() async {
        let hold = Rendezvous()
        let harness = Harness(status: .init(rendezvous: hold))
        let model = await harness.model()
        let write = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)

        await model.reorder(ids(market, rent), of: .expense, in: october)

        #expect(harness.reorder.calls == [ids(market, gym, rent)])
        #expect(model.state.writes.isWriting(rent.id))
        await hold.release()
        await write.value
    }

    @Test("overlapping drags both send, the second on top of the first, and the second's order stands")
    func overlapping() async {
        let hold = Rendezvous()
        let harness = Harness(reorder: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.reorder(ids(market, rent), of: .expense, in: october) }
        await hold.untilArrivals(1)
        let second = Task { await model.reorder(ids(gym, market), of: .expense, in: october) }
        await hold.untilArrivals(2)

        await hold.release()
        await first.value
        await second.value

        #expect(harness.reorder.calls == [ids(market, gym, rent), ids(gym, market, rent)])
        #expect(expenses(model).map(\.id) == ids(gym, market, rent))
        #expect(harness.source.calls == [october])
    }
}
