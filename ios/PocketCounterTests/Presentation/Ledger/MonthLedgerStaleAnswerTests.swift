import Testing

@testable import PocketCounter

private let october = RefYearMonth(raw: 202610)!

private let rent = HistoryItem.fixture(id: "rent", amount: -10, statusPayment: .pending, displayOrder: 0)
private let gym = HistoryItem.fixture(id: "gym", amount: -20, statusPayment: .pending, displayOrder: 1)
private let market = HistoryItem.fixture(id: "market", amount: -30, statusPayment: .pending, displayOrder: 2)

private func ids(_ items: HistoryItem...) -> [TransactionID] {
    items.map(\.id)
}

@MainActor
private struct Harness {
    let source = LedgerSourceFake()
    let expiry = ExpirySignal()
    let hold = Rendezvous()
    let status: WriteProbe<TransactionID>
    let fixo = WriteProbe<HistoryItem>()
    let delete = WriteProbe<TransactionID>()
    let reorder = WriteProbe<[TransactionID]>()

    init(status: Result<Void, WriteFailure> = .success(())) {
        self.status = WriteProbe(status)
    }

    func model() async -> MonthLedgerModel {
        source.ledgers = [october: MonthLedger(ref: october, items: [rent, gym, market], lookups: .fixture())]
        let status = status, fixo = fixo, delete = delete, reorder = reorder
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action,
            setPaymentStatus: { id, _ throws(WriteFailure) in try await status.run(id) },
            toggleFixo: { item throws(WriteFailure) in try await fixo.run(item) },
            deleteTransaction: { id throws(WriteFailure) in try await delete.run(id) },
            reorderTransactions: { ids throws(WriteFailure) in try await reorder.run(ids) },
            onSessionExpired: expiry.action
        )
        await model.load()
        return model
    }

    /// Issues a refresh whose answer is the ledger as it stands now and commits only on `release`.
    func issueLoad(on model: MonthLedgerModel) async -> Task<Void, Never> {
        source.rendezvous = hold
        let load = Task { await model.refresh() }
        await hold.untilArrivals(1)
        return load
    }

    func release(_ load: Task<Void, Never>) async {
        await hold.release()
        await load.value
    }
}

@MainActor
@Suite("MonthLedgerModel stale answers")
struct MonthLedgerStaleAnswerTests {
    @Test("a load issued before a delete does not bring the row back")
    func deletion() async {
        let harness = Harness()
        let model = await harness.model()
        let load = await harness.issueLoad(on: model)

        await model.delete(rent)
        await harness.release(load)

        #expect(model.state.load.value?.items.map(\.id) == ids(gym, market))
        #expect(model.state.months[october]?.value?.items.map(\.id) == ids(rent, gym, market))
    }

    @Test("a load issued before a drag does not snap the order back")
    func reorder() async {
        let harness = Harness()
        let model = await harness.model()
        let load = await harness.issueLoad(on: model)

        await model.reorder(ids(market, rent), of: .expense, in: october)
        await harness.release(load)

        #expect(model.state.load.value?.items.map(\.id) == ids(market, gym, rent))
    }

    @Test("a load issued before a status write's failure does not erase its notice")
    func failureNotice() async {
        let harness = Harness(status: .failure(.server))
        let model = await harness.model()
        let load = await harness.issueLoad(on: model)

        await model.togglePaymentStatus(of: rent)
        await harness.release(load)

        #expect(model.state.writes.statuses[rent.id]?.phase == .failed(.server))
    }

    @Test("a load issued before a status write's success keeps the new status")
    func successStatus() async {
        let harness = Harness()
        let model = await harness.model()
        let load = await harness.issueLoad(on: model)

        await model.togglePaymentStatus(of: rent)
        await harness.release(load)

        #expect(model.state.load.value?.items.first { $0.id == rent.id }?.statusPayment == .paid)
    }

    @Test("the next load, issued after the failure, retires the notice")
    func nextLoadRetires() async {
        let harness = Harness(status: .failure(.server))
        let model = await harness.model()
        let load = await harness.issueLoad(on: model)
        await model.togglePaymentStatus(of: rent)
        await harness.release(load)

        await model.refresh()

        #expect(model.state.writes.statuses.isEmpty)
    }

    @Test("a fixo toggle's reload does not erase a failure recorded during it")
    func fixoReload() async {
        let harness = Harness(status: .failure(.server))
        let model = await harness.model()
        harness.source.rendezvous = harness.hold
        let toggle = Task { await model.toggleFixo(of: gym) }
        await harness.hold.untilArrivals(1)

        await model.togglePaymentStatus(of: rent)
        await harness.hold.release()
        await toggle.value

        #expect(model.state.writes.statuses[rent.id]?.phase == .failed(.server))
        #expect(model.state.writes.intents.isEmpty)
    }

    @Test("a row the server already deleted is not deleted twice")
    func noSecondDelete() async {
        let harness = Harness()
        let model = await harness.model()

        await model.delete(rent)
        await model.delete(rent)

        #expect(harness.delete.calls == [rent.id])
    }
}
