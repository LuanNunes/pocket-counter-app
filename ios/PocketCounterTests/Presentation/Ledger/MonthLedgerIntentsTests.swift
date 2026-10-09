import Testing

@testable import PocketCounter

private let october = RefYearMonth(raw: 202610)!
private let november = RefYearMonth(raw: 202611)!

private func ledger(_ ref: RefYearMonth = october, _ items: HistoryItem...) -> MonthLedger {
    MonthLedger(ref: ref, items: items, lookups: .fixture())
}

private let rent = HistoryItem.fixture(id: "rent", amount: -10, statusPayment: .pending)
private let gym = HistoryItem.fixture(id: "gym", amount: -20, statusPayment: .pending)
private let salary = HistoryItem.fixture(id: "salary", amount: 100, type: .income, statusPayment: .paid)

@MainActor
private struct Harness {
    let source = LedgerSourceFake()
    let expiry = ExpirySignal()
    let status: WriteProbe<TransactionID>
    let fixo: WriteProbe<HistoryItem>
    let delete: WriteProbe<TransactionID>

    init(
        status: WriteProbe<TransactionID> = .init(),
        fixo: WriteProbe<HistoryItem> = .init(),
        delete: WriteProbe<TransactionID> = .init()
    ) {
        self.status = status
        self.fixo = fixo
        self.delete = delete
    }

    func model(items: [HistoryItem] = [rent, gym, salary], loaded: Bool = true) async -> MonthLedgerModel {
        source.ledgers = [october: ledger(october, items: items), november: ledger(november)]
        let status = status, fixo = fixo, delete = delete
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action,
            setPaymentStatus: { id, _ throws(WriteFailure) in try await status.run(id) },
            toggleFixo: { item throws(WriteFailure) in try await fixo.run(item) },
            deleteTransaction: { id throws(WriteFailure) in try await delete.run(id) },
            onSessionExpired: expiry.action
        )
        if loaded { await model.load() }
        return model
    }
}

private func ledger(_ ref: RefYearMonth, items: [HistoryItem]) -> MonthLedger {
    MonthLedger(ref: ref, items: items, lookups: .fixture())
}

@MainActor
@Suite("MonthLedgerModel deleting")
struct MonthLedgerDeleteTests {
    @Test("a delete removes that row, sends its id once and remembers it as settled")
    func success() async {
        let harness = Harness()
        let model = await harness.model()

        await model.delete(rent)

        #expect(harness.delete.calls == [rent.id])
        #expect(model.state.load.value == ledger(october, gym, salary))
        #expect(model.state.writes.intents[rent.id]?.phase == .settled(.deletion))
    }

    @Test("a delete makes no reload: the answer is the removal")
    func noReload() async {
        let harness = Harness()
        let model = await harness.model()

        await model.delete(rent)

        #expect(harness.source.calls == [october])
    }

    @Test("a row already gone on the server is deleted all the same: the outcome is identical to success")
    func vanishedIsSuccess() async {
        let ok = Harness()
        let gone = Harness(delete: .init(.failure(.vanished)))
        let okModel = await ok.model()
        let goneModel = await gone.model()

        await okModel.delete(rent)
        await goneModel.delete(rent)

        #expect(goneModel.state == okModel.state)
        #expect(gone.expiry.count == 0)
    }

    @Test("a failed delete keeps the row, records the intent and does not make the month stale", arguments: [
        WriteFailure.unreachable, .server, .authenticationUnavailable, .rejected("Não pode"),
    ])
    func failure(failure: WriteFailure) async {
        let harness = Harness(delete: .init(.failure(failure)))
        let model = await harness.model()
        let before = model.state.load

        await model.delete(rent)

        #expect(model.state.writes.intents[rent.id] == RowIntentWrite(ref: october, phase: .failed(failure), attempted: .deletion))
        #expect(model.state.load == before)
        #expect(model.state.load.phase == .loaded(ledger(october, rent, gym, salary)))
        #expect(!model.state.writes.isWriting(rent.id))
        #expect(harness.expiry.count == 0)
    }

    @Test("an expired session signals once, records nothing and leaves the ledger as it was")
    func sessionExpired() async {
        let harness = Harness(delete: .init(.failure(.sessionExpired)))
        let model = await harness.model()
        let before = model.state

        await model.delete(rent)

        #expect(harness.expiry.count == 1)
        #expect(model.state == before)
    }

    @Test("a failed status write on the row does not outlive the row")
    func clearsFailedStatusWrite() async {
        let harness = Harness(status: .init(.failure(.server)))
        let model = await harness.model()
        await model.togglePaymentStatus(of: rent)
        #expect(model.state.writes.statuses[rent.id]?.phase == .failed(.server))

        await model.delete(rent)

        #expect(model.state.writes.statuses.isEmpty)
    }

    @Test("a delete that lands after the month changed still removes the row from its own month")
    func landsAfterSelect() async {
        let hold = Rendezvous()
        let harness = Harness(delete: .init(rendezvous: hold))
        let model = await harness.model()
        let task = Task { await model.delete(rent) }
        await hold.untilArrivals(1)
        model.select(november)
        await model.load()
        let novemberBefore = model.state.months[november]

        await hold.release()
        await task.value

        #expect(model.state.months[october]?.value == ledger(october, rent, gym, salary))
        #expect(model.state.ledger(for: october) == ledger(october, gym, salary))
        #expect(model.state.months[november] == novemberBefore)
        #expect(model.state.writes.intents[rent.id]?.phase == .settled(.deletion))
    }

    @Test("a second delete while the first is in flight sends nothing")
    func secondTap() async {
        let hold = Rendezvous()
        let harness = Harness(delete: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.delete(rent) }
        await hold.untilArrivals(1)

        await model.delete(rent)

        #expect(harness.delete.calls.count == 1)
        await hold.release()
        await first.value
    }

    @Test("a row the overlaid ledger does not hold sends nothing")
    func placeholder() async {
        let harness = Harness()
        let model = await harness.model()
        let unloaded = await Harness().model(loaded: false)

        await model.delete(.fixture(id: "placeholder-1"))
        await unloaded.delete(rent)

        #expect(harness.delete.calls.isEmpty)
        #expect(model.state.writes.intents.isEmpty)
        #expect(unloaded.state.writes.intents.isEmpty)
    }

    @Test("two rows delete side by side and both leave")
    func overlapping() async {
        let hold = Rendezvous()
        let harness = Harness(delete: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.delete(rent) }
        let second = Task { await model.delete(gym) }
        await hold.untilArrivals(2)

        await hold.release()
        await first.value
        await second.value

        #expect(model.state.load.value == ledger(october, salary))
        #expect(model.state.writes.intents[rent.id]?.phase == .settled(.deletion))
        #expect(model.state.writes.intents[gym.id]?.phase == .settled(.deletion))
    }
}

private let standingOrder = HistoryItem.fixture(id: "rent", amount: -10, statusPayment: .pending, seriesId: "s1")

@MainActor
@Suite("MonthLedgerModel toggling fixo")
struct MonthLedgerFixoTests {
    @Test("the verb receives the row as displayed")
    func sendsItem() async {
        let harness = Harness()
        let model = await harness.model()

        await model.toggleFixo(of: rent)

        #expect(harness.fixo.calls == [rent])
    }

    @Test("the intent in flight asks for the opposite of what the row shows", arguments: [
        (rent, true), (standingOrder, false),
    ])
    func target(item: HistoryItem, expected: Bool) async {
        let hold = Rendezvous()
        let harness = Harness(fixo: .init(rendezvous: hold))
        let model = await harness.model(items: [item, gym, salary])
        let task = Task { await model.toggleFixo(of: item) }
        await hold.untilArrivals(1)

        #expect(model.state.writes.intents[item.id] == RowIntentWrite(ref: october, phase: .inFlight(.fixo(expected))))
        #expect(model.state.load.value == ledger(october, item, gym, salary))

        await hold.release()
        await task.value
    }

    @Test("a success reloads the month once and commits what the server now says")
    func success() async {
        let harness = Harness()
        let model = await harness.model()
        let retagged = HistoryItem.fixture(
            id: "rent", amount: -10, tagIds: [.of("g1")], statusPayment: .pending, seriesId: "s1")
        harness.source.ledgers[october] = ledger(october, retagged, gym, salary)

        await model.toggleFixo(of: rent)

        #expect(harness.source.calls == [october, october])
        #expect(model.state.load.value == ledger(october, retagged, gym, salary))
        #expect(model.state.writes.intents.isEmpty)
        #expect(!model.state.load.isLoading)
    }

    @Test("a success after the month changed reloads nothing, drops the intent and leaves the other month alone")
    func monthChanged() async {
        let hold = Rendezvous()
        let harness = Harness(fixo: .init(rendezvous: hold))
        let model = await harness.model()
        let task = Task { await model.toggleFixo(of: rent) }
        await hold.untilArrivals(1)
        model.select(november)
        await model.load()
        let novemberBefore = model.state.months[november]

        await hold.release()
        await task.value

        #expect(harness.source.calls == [october, november])
        #expect(model.state.writes.intents.isEmpty)
        #expect(model.state.months[november] == novemberBefore)
        #expect(!model.state.load.isLoading)
    }

    @Test("a failed reload leaves the month stale and the intent dropped")
    func reloadFails() async {
        let harness = Harness()
        let model = await harness.model()
        harness.source.failure = .unreachable

        await model.toggleFixo(of: rent)

        #expect(model.state.load.phase == .stale(ledger(october, rent, gym, salary), .unreachable))
        #expect(model.state.writes.intents.isEmpty)
    }

    @Test("a failure records the intent, reloads nothing and does not make the month stale", arguments: [
        WriteFailure.unreachable, .server, .authenticationUnavailable, .rejected("Não pode"), .vanished,
    ])
    func failure(failure: WriteFailure) async {
        let harness = Harness(fixo: .init(.failure(failure)))
        let model = await harness.model()

        await model.toggleFixo(of: rent)

        #expect(model.state.writes.intents[rent.id] == RowIntentWrite(ref: october, phase: .failed(failure), attempted: .fixo(true)))
        #expect(harness.source.calls == [october])
        #expect(model.state.load.phase == .loaded(ledger(october, rent, gym, salary)))
        #expect(harness.expiry.count == 0)
    }

    @Test("an expired session signals once, records nothing and reloads nothing")
    func sessionExpired() async {
        let harness = Harness(fixo: .init(.failure(.sessionExpired)))
        let model = await harness.model()
        let before = model.state

        await model.toggleFixo(of: rent)

        #expect(harness.expiry.count == 1)
        #expect(model.state == before)
        #expect(harness.source.calls == [october])
    }

    @Test("two rows toggled side by side both end with no intent and no spinner")
    func overlapping() async {
        let hold = Rendezvous()
        let harness = Harness(fixo: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.toggleFixo(of: rent) }
        let second = Task { await model.toggleFixo(of: gym) }
        await hold.untilArrivals(2)

        await hold.release()
        await first.value
        await second.value

        #expect(model.state.writes.intents.isEmpty)
        #expect(!model.state.load.isLoading)
        #expect(model.state.load.phase == .loaded(ledger(october, rent, gym, salary)))
    }

    @Test("a row the overlaid ledger does not hold sends nothing")
    func placeholder() async {
        let harness = Harness()
        let model = await harness.model()
        let unloaded = await Harness().model(loaded: false)

        await model.toggleFixo(of: .fixture(id: "placeholder-1"))
        await unloaded.toggleFixo(of: rent)

        #expect(harness.fixo.calls.isEmpty)
        #expect(model.state.writes.intents.isEmpty)
        #expect(unloaded.state.writes.intents.isEmpty)
    }
}

@MainActor
@Suite("MonthLedgerModel one door per row")
struct MonthLedgerDoorTests {
    @Test("a status write in flight closes the door to fixo")
    func statusThenFixo() async {
        let hold = Rendezvous()
        let harness = Harness(status: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)

        await model.toggleFixo(of: rent)

        #expect(harness.fixo.calls.isEmpty)
        await hold.release()
        await first.value
    }

    @Test("a fixo toggle in flight closes the door to a status write")
    func fixoThenStatus() async {
        let hold = Rendezvous()
        let harness = Harness(fixo: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.toggleFixo(of: rent) }
        await hold.untilArrivals(1)

        await model.togglePaymentStatus(of: rent)

        #expect(harness.status.calls.isEmpty)
        await hold.release()
        await first.value
    }

    @Test("a status write in flight closes the door to a delete")
    func statusThenDelete() async {
        let hold = Rendezvous()
        let harness = Harness(status: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.togglePaymentStatus(of: rent) }
        await hold.untilArrivals(1)

        await model.delete(rent)

        #expect(harness.delete.calls.isEmpty)
        await hold.release()
        await first.value
    }

    @Test("a delete in flight closes the door to a status write")
    func deleteThenStatus() async {
        let hold = Rendezvous()
        let harness = Harness(delete: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.delete(rent) }
        await hold.untilArrivals(1)

        await model.togglePaymentStatus(of: rent)

        #expect(harness.status.calls.isEmpty)
        await hold.release()
        await first.value
    }

    @Test("a fixo toggle in flight closes the door to a delete")
    func fixoThenDelete() async {
        let hold = Rendezvous()
        let harness = Harness(fixo: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.toggleFixo(of: rent) }
        await hold.untilArrivals(1)

        await model.delete(rent)

        #expect(harness.delete.calls.isEmpty)
        await hold.release()
        await first.value
    }

    @Test("a delete in flight closes the door to a fixo toggle")
    func deleteThenFixo() async {
        let hold = Rendezvous()
        let harness = Harness(delete: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.delete(rent) }
        await hold.untilArrivals(1)

        await model.toggleFixo(of: rent)

        #expect(harness.fixo.calls.isEmpty)
        await hold.release()
        await first.value
    }

    @Test("a different row is not behind the door")
    func otherRow() async {
        let hold = Rendezvous()
        let harness = Harness(delete: .init(rendezvous: hold))
        let model = await harness.model()
        let first = Task { await model.delete(rent) }
        await hold.untilArrivals(1)

        await model.togglePaymentStatus(of: gym)

        #expect(harness.status.calls == [gym.id])
        await hold.release()
        await first.value
    }
}
