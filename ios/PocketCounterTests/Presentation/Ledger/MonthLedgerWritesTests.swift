import Testing

@testable import PocketCounter

@MainActor
private final class LedgerSource {
    var ledgers: [RefYearMonth: MonthLedger] = [:]
    var failure: LoadFailure?
    var rendezvous: Rendezvous?

    var action: LoadMonthAction {
        { [self] ref throws(LoadFailure) in try await answer(ref) }
    }

    private func answer(_ ref: RefYearMonth) async throws(LoadFailure) -> MonthLedger {
        let hold = rendezvous
        let failure = failure
        let ledger = ledgers[ref] ?? MonthLedger(ref: ref, items: [], lookups: .fixture())
        await hold?.arrive()
        if let failure { throw failure }
        return ledger
    }
}

@MainActor
private final class ExpiryCounter {
    private(set) var count = 0

    var action: SessionExpiredAction {
        { [self] in count += 1 }
    }
}

private let october = RefYearMonth(raw: 202610)!
private let november = RefYearMonth(raw: 202611)!

private func ledger(_ ref: RefYearMonth = october, _ items: HistoryItem...) -> MonthLedger {
    MonthLedger(ref: ref, items: items, lookups: .fixture())
}

private let rent = HistoryItem.fixture(id: "rent", amount: -10, statusPayment: .pending)
private let gym = HistoryItem.fixture(id: "gym", amount: -20, statusPayment: .pending)
private let salary = HistoryItem.fixture(id: "salary", amount: 100, type: .income, statusPayment: .paid)

private func status(of item: HistoryItem, in state: MonthLedgerModel.State) -> PaymentStatus? {
    state.load.value?.items.first { $0.id == item.id }?.statusPayment
}

@MainActor
@Suite("MonthLedgerModel.State writes")
struct MonthLedgerStateWritesTests {
    private let source = LedgerSource()
    private let expiry = ExpiryCounter()

    private func loadedModel() async -> MonthLedgerModel {
        source.ledgers = [october: ledger(october, rent, gym, salary), november: ledger(november)]
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action, onSessionExpired: expiry.action
        )
        await model.load()
        return model
    }

    @Test("the overlay shows the target while the committed ledger still holds the server's answer")
    func overlay() async {
        var state = await loadedModel().state

        state.beginStatus(rent.id, ref: october, target: .paid)

        #expect(status(of: rent, in: state) == .paid)
        #expect(state.months[october]?.value?.items.first { $0.id == rent.id } == rent)
        #expect(state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .inFlight(.paid)))
    }

    @Test("Início's pending figure moves with the overlay by exactly the row's magnitude")
    func pendingFigure() async throws {
        var state = await loadedModel().state
        let before = try #require(state.load.value).kpis

        state.beginStatus(rent.id, ref: october, target: .paid)

        let after = try #require(state.load.value).kpis
        #expect(after.pendingTotal == Money(20))
        #expect(before.pendingTotal == Money(30))
        #expect(after.pendingCount == before.pendingCount - 1)
    }

    @Test("completing settles the overlay and leaves the server's answer as it was")
    func complete() async {
        var state = await loadedModel().state
        state.beginStatus(rent.id, ref: october, target: .paid)

        state.completeWrite(rent.id, ref: october, target: .paid)

        #expect(state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .settled(.paid)))
        #expect(state.months[october]?.value?.items.first { $0.id == rent.id }?.statusPayment == .pending)
        #expect(state.load.value?.items.first { $0.id == rent.id }?.statusPayment == .paid)
    }

    @Test("completing leaves a pending refresh and a standing failure alone")
    func completeKeepsLoadFlags() async {
        let model = await loadedModel()
        source.failure = .server
        await model.refresh()
        source.failure = nil
        let hold = Rendezvous()
        source.rendezvous = hold
        let refresh = Task { await model.refresh() }
        await hold.untilArrivals(1)
        var state = model.state
        state.beginStatus(rent.id, ref: october, target: .paid)

        state.completeWrite(rent.id, ref: october, target: .paid)

        #expect(state.load.isLoading)
        #expect(state.load.failure == .server)
        await hold.release()
        await refresh.value
    }

    @Test("a failure snaps the row back and records itself")
    func fail() async {
        var state = await loadedModel().state
        state.beginStatus(rent.id, ref: october, target: .paid)

        state.failStatus(rent.id, ref: october, .server)

        #expect(status(of: rent, in: state) == .pending)
        #expect(state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .failed(.server)))
        #expect(!state.writes.isWriting(rent.id))
    }

    @Test("a new attempt replaces the failure")
    func beginOverFailure() async {
        var state = await loadedModel().state
        state.beginStatus(rent.id, ref: october, target: .paid)
        state.failStatus(rent.id, ref: october, .server)

        state.beginStatus(rent.id, ref: october, target: .paid)

        #expect(state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .inFlight(.paid)))
        #expect(state.writes.isWriting(rent.id))
    }

    @Test("a fresh answer for a month keeps in-flight writes and drops only that month's failures")
    func commitLifecycle() async {
        var state = await loadedModel().state
        let other = TransactionID(rawValue: "nov")
        state.beginStatus(rent.id, ref: october, target: .paid)
        state.beginStatus(gym.id, ref: october, target: .paid)
        state.failStatus(gym.id, ref: october, .server)
        state.beginStatus(other, ref: november, target: .paid)
        state.failStatus(other, ref: november, .server)

        state.commit(ledger(october, rent, gym, salary), for: october, at: state.writes.revision)

        #expect(state.writes.statuses == [
            rent.id: PaymentStatusWrite(ref: october, phase: .inFlight(.paid)),
            other: PaymentStatusWrite(ref: november, phase: .failed(.server)),
        ])
    }

    @Test("an October overlay leaves November's ledger untouched")
    func otherMonth() async {
        let model = await loadedModel()
        model.select(november)
        await model.load()
        model.select(october)
        var state = model.state
        state.beginStatus(rent.id, ref: october, target: .paid)

        state.month = november

        #expect(state.load.phase == .loaded(ledger(november)))
    }

    @Test("a refresh landing mid-flight still shows the target over the refreshed ledger")
    func commitMidFlight() async {
        var state = await loadedModel().state
        let extra = HistoryItem.fixture(id: "extra", amount: -5, statusPayment: .pending)
        state.beginStatus(rent.id, ref: october, target: .paid)

        state.commit(ledger(october, rent, extra), for: october, at: state.writes.revision)

        #expect(state.load.value == ledger(october, rent.settingPaymentStatus(.paid), extra))
    }

    @Test("only a row of that month's overlaid ledger counts as held")
    func holdsRow() async {
        let model = await loadedModel()
        let fresh = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action, onSessionExpired: expiry.action
        )

        #expect(model.state.holdsRow(rent.id, in: october))
        #expect(!model.state.holdsRow(rent.id, in: november))
        #expect(!model.state.holdsRow(TransactionID(rawValue: "placeholder"), in: october))
        #expect(!fresh.state.holdsRow(rent.id, in: october))
    }
}

@MainActor
@Suite("MonthLedgerModel toggling")
struct MonthLedgerToggleTests {
    private let source = LedgerSource()
    private let expiry = ExpiryCounter()
    private let hold = Rendezvous()

    private func model(
        _ repository: FakeTransactionRepository, items: [HistoryItem] = [rent, gym, salary], loaded: Bool = true
    ) async -> MonthLedgerModel {
        source.ledgers = [october: MonthLedger(ref: october, items: items, lookups: .fixture())]
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action,
            setPaymentStatus: { id, status throws(WriteFailure) in
                try await repository.setPaymentStatus(status, on: id)
            },
            onSessionExpired: expiry.action
        )
        if loaded { await model.load() }
        return model
    }

    @Test("a toggle sends the opposite of the displayed status, once, for that row", arguments: [
        (PaymentStatus.pending, PaymentStatus.paid), (.paid, .pending),
    ])
    func sendsOpposite(displayed: PaymentStatus, expected: PaymentStatus) async {
        let item = rent.settingPaymentStatus(displayed)
        let repository = FakeTransactionRepository()
        let model = await model(repository, items: [gym, item])

        await model.togglePaymentStatus(of: item)

        #expect(repository.writes.calls == [.init(id: item.id, status: expected)])
        #expect(model.state.writes.statuses[item.id]?.phase == .settled(expected))
        #expect(status(of: item, in: model.state) == expected)
    }

    @Test("a second tap while the first is in flight sends nothing")
    func secondTap() async {
        var repository = FakeTransactionRepository()
        repository.writeRendezvous = hold
        let model = await model(repository)
        let first = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)

        await model.togglePaymentStatus(of: rent)

        #expect(repository.writes.calls.count == 1)
        await hold.release()
        await first.value
    }

    @Test("a tap before the month has a committed ledger sends nothing")
    func firstLoadTap() async {
        let repository = FakeTransactionRepository()
        let model = await model(repository, loaded: false)

        await model.togglePaymentStatus(of: rent)

        #expect(repository.writes.calls.isEmpty)
        #expect(model.state.writes.statuses.isEmpty)
    }

    @Test("a tap on a row the overlaid ledger does not hold sends nothing")
    func placeholderTap() async {
        let repository = FakeTransactionRepository()
        let model = await model(repository)

        await model.togglePaymentStatus(of: .fixture(id: "placeholder-1", statusPayment: .pending))

        #expect(repository.writes.calls.isEmpty)
        #expect(model.state.writes.statuses.isEmpty)
    }

    @Test("two rows overlap and both land")
    func overlapping() async {
        var repository = FakeTransactionRepository()
        repository.writeRendezvous = hold
        let model = await model(repository)
        let first = Task { await model.togglePaymentStatus(of: rent) }
        let second = Task { await model.togglePaymentStatus(of: gym) }
        await hold.untilArrivals(2)

        await hold.release()
        await first.value
        await second.value

        #expect(repository.writes.calls.count == 2)
        #expect(status(of: rent, in: model.state) == .paid)
        #expect(status(of: gym, in: model.state) == .paid)
        #expect(model.state.writes.statuses[rent.id]?.phase == .settled(.paid))
        #expect(model.state.writes.statuses[gym.id]?.phase == .settled(.paid))
    }

    @Test("an expired session signals once, records no row failure and leaves the ledger alone")
    func sessionExpired() async {
        var repository = FakeTransactionRepository()
        repository.writeResult = .failure(.sessionExpired)
        let model = await model(repository)
        let before = model.state.load

        await model.togglePaymentStatus(of: rent)

        #expect(expiry.count == 1)
        #expect(model.state.writes.statuses.isEmpty)
        #expect(model.state.load == before)
    }

    @Test("a failed write records the failure and the row reads as before")
    func failure() async {
        var repository = FakeTransactionRepository()
        repository.writeResult = .failure(.unreachable)
        let model = await model(repository)

        await model.togglePaymentStatus(of: rent)

        #expect(model.state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .failed(.unreachable)))
        #expect(status(of: rent, in: model.state) == .pending)
        #expect(expiry.count == 0)
    }

    @Test("a write that lands after the month changed updates the month it was made in")
    func landsAfterSelect() async {
        var repository = FakeTransactionRepository()
        repository.writeRendezvous = hold
        let model = await model(repository)
        let write = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)
        model.select(november)

        await hold.release()
        await write.value
        model.select(october)

        #expect(status(of: rent, in: model.state) == .paid)
        #expect(model.state.writes.statuses[rent.id]?.phase == .settled(.paid))
    }

    @Test("a refresh landing mid-flight yields the promoted value over the refreshed ledger")
    func refreshMidFlight() async {
        var repository = FakeTransactionRepository()
        repository.writeRendezvous = hold
        let model = await model(repository)
        let extra = HistoryItem.fixture(id: "extra", amount: -5, statusPayment: .pending)
        let write = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)
        source.ledgers = [october: ledger(october, rent, gym, salary, extra)]
        await model.refresh()

        await hold.release()
        await write.value

        #expect(model.state.load.value == ledger(october, rent.settingPaymentStatus(.paid), gym, salary, extra))
        #expect(model.state.writes.statuses[rent.id]?.phase == .settled(.paid))
    }

    @Test("a failure over a stale month keeps the stale notice and the loading flag")
    func failureOverStale() async {
        var repository = FakeTransactionRepository()
        repository.writeResult = .failure(.server)
        let model = await model(repository)
        source.failure = .unreachable
        await model.refresh()
        source.failure = nil
        let reload = Rendezvous()
        source.rendezvous = reload
        let refresh = Task { await model.refresh() }
        await reload.untilArrivals(1)

        await model.togglePaymentStatus(of: rent)

        #expect(model.state.load.failure == .unreachable)
        #expect(model.state.load.isLoading)
        #expect(model.state.writes.statuses[rent.id]?.phase == .failed(.server))
        await reload.release()
        await refresh.value
    }

    @Test("changing month with a load in flight leaves writes alone")
    func selectKeepsWrites() async {
        var repository = FakeTransactionRepository()
        repository.writeRendezvous = hold
        let model = await model(repository)
        let write = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)
        let reload = Rendezvous()
        source.rendezvous = reload
        let refresh = Task { await model.refresh() }
        await reload.untilArrivals(1)

        model.select(november)

        #expect(model.state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .inFlight(.paid)))
        await reload.release()
        await refresh.value
        await hold.release()
        await write.value
    }

    @Test("cancelling a load in flight leaves writes alone")
    func cancelKeepsWrites() async {
        var repository = FakeTransactionRepository()
        repository.writeRendezvous = hold
        let model = await model(repository)
        let write = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)
        let reload = Rendezvous()
        source.rendezvous = reload
        let refresh = Task { await model.refresh() }
        await reload.untilArrivals(1)

        model.cancel()

        #expect(model.state.writes.statuses[rent.id] == PaymentStatusWrite(ref: october, phase: .inFlight(.paid)))
        await reload.release()
        await refresh.value
        await hold.release()
        await write.value
    }
}
