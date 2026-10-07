import Testing

@testable import PocketCounter

private let october = RefYearMonth(raw: 202610)!
private let november = RefYearMonth(raw: 202611)!

private let rent = HistoryItem.fixture(id: "rent", amount: -10, statusPayment: .pending)
private let gym = HistoryItem.fixture(id: "gym", amount: -20, statusPayment: .pending)
private let salary = HistoryItem.fixture(id: "salary", amount: 100, type: .income, statusPayment: .paid)

private func ledger(_ ref: RefYearMonth = october, _ items: HistoryItem...) -> MonthLedger {
    MonthLedger(ref: ref, items: items, lookups: .fixture())
}

@MainActor
@Suite("MonthLedgerModel.State intents")
struct MonthLedgerStateIntentsTests {
    private let source = LedgerSourceFake()
    private let expiry = ExpirySignal()

    private func loadedModel() async -> MonthLedgerModel {
        source.ledgers = [october: ledger(october, rent, gym, salary), november: ledger(november)]
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action, onSessionExpired: expiry.action)
        await model.load()
        return model
    }

    @Test("an intent never reaches the committed ledger the screens share")
    func notProjected() async {
        var state = await loadedModel().state
        let before = state.load

        state.beginIntent(rent.id, ref: october, target: .fixo(true))
        state.beginIntent(gym.id, ref: october, target: .deletion)

        #expect(state.load == before)
        #expect(state.writes.intents[rent.id] == RowIntentWrite(ref: october, phase: .inFlight(.fixo(true))))
    }

    @Test("one door per row across both maps", arguments: [true, false])
    func sharedDoor(statusFirst: Bool) async {
        var state = await loadedModel().state
        #expect(!state.writes.isWriting(rent.id))

        if statusFirst {
            state.beginStatus(rent.id, ref: october, target: .paid)
        } else {
            state.beginIntent(rent.id, ref: october, target: .deletion)
        }

        #expect(state.writes.isWriting(rent.id))
        #expect(!state.writes.isWriting(gym.id))
    }

    @Test("a failed write of either kind does not hold the door")
    func failedDoesNotHold() async {
        var state = await loadedModel().state
        state.failStatus(rent.id, ref: october, .server)
        state.beginIntent(gym.id, ref: october, target: .deletion)
        state.failIntent(gym.id, ref: october, .server)

        #expect(!state.writes.isWriting(rent.id))
        #expect(!state.writes.isWriting(gym.id))
        #expect(state.writes.intents[gym.id] == RowIntentWrite(ref: october, phase: .failed(.server), attempted: .deletion))
    }

    @Test("a failure replaces the in-flight intent and a new attempt replaces the failure")
    func failThenRetry() async {
        var state = await loadedModel().state
        state.beginIntent(rent.id, ref: october, target: .deletion)
        state.failIntent(rent.id, ref: october, .unreachable)
        #expect(state.writes.intents[rent.id] == RowIntentWrite(ref: october, phase: .failed(.unreachable), attempted: .deletion))

        state.beginIntent(rent.id, ref: october, target: .deletion)

        #expect(state.writes.intents[rent.id]?.phase == .inFlight(.deletion))
    }

    @Test("dropping an intent forgets it")
    func drop() async {
        var state = await loadedModel().state
        state.beginIntent(rent.id, ref: october, target: .fixo(true))

        state.dropIntent(rent.id)

        #expect(state.writes.intents.isEmpty)
    }

    @Test("completing a deletion removes the row from its month and clears the intent")
    func completeDeletion() async {
        var state = await loadedModel().state
        state.beginIntent(rent.id, ref: october, target: .deletion)

        state.completeDeletion(rent.id, ref: october)

        #expect(state.writes.intents.isEmpty)
        #expect(state.load.value == ledger(october, gym, salary))
    }

    @Test("completing a deletion also clears a failed status write for that row")
    func completeDeletionClearsFailedStatus() async {
        var state = await loadedModel().state
        state.beginStatus(rent.id, ref: october, target: .paid)
        state.failStatus(rent.id, ref: october, .server)
        state.beginIntent(rent.id, ref: october, target: .deletion)

        state.completeDeletion(rent.id, ref: october)

        #expect(state.writes.statuses.isEmpty)
    }

    @Test("completing a deletion leaves other rows' writes, the loading flag and a standing failure alone")
    func completeDeletionKeepsTheRest() async {
        let model = await loadedModel()
        source.failure = .server
        await model.refresh()
        source.failure = nil
        let hold = Rendezvous()
        source.rendezvous = hold
        let refresh = Task { await model.refresh() }
        await hold.untilArrivals(1)
        var state = model.state
        state.beginStatus(gym.id, ref: october, target: .paid)
        state.beginIntent(rent.id, ref: october, target: .deletion)

        state.completeDeletion(rent.id, ref: october)

        #expect(state.load.isLoading)
        #expect(state.load.failure == .server)
        #expect(state.writes.statuses[gym.id] == PaymentStatusWrite(ref: october, phase: .inFlight(.paid)))
        await hold.release()
        await refresh.value
    }

    @Test("a fresh answer for a month keeps in-flight entries and drops failed ones, in both maps, for that month only")
    func commitLifecycle() async {
        var state = await loadedModel().state
        let other = TransactionID(rawValue: "nov")
        let otherIntent = TransactionID(rawValue: "nov-intent")
        state.beginStatus(rent.id, ref: october, target: .paid)
        state.beginStatus(gym.id, ref: october, target: .paid)
        state.failStatus(gym.id, ref: october, .server)
        state.beginStatus(other, ref: november, target: .paid)
        state.failStatus(other, ref: november, .server)
        state.beginIntent(salary.id, ref: october, target: .fixo(true))
        let failedIntent = TransactionID(rawValue: "failed-intent")
        state.beginIntent(failedIntent, ref: october, target: .deletion)
        state.failIntent(failedIntent, ref: october, .server)
        state.beginIntent(otherIntent, ref: november, target: .deletion)
        state.failIntent(otherIntent, ref: november, .server)

        state.commit(ledger(october, rent, gym, salary), for: october)

        #expect(state.writes.statuses == [
            rent.id: PaymentStatusWrite(ref: october, phase: .inFlight(.paid)),
            other: PaymentStatusWrite(ref: november, phase: .failed(.server)),
        ])
        #expect(state.writes.intents == [
            salary.id: RowIntentWrite(ref: october, phase: .inFlight(.fixo(true))),
            otherIntent: RowIntentWrite(ref: november, phase: .failed(.server), attempted: .deletion),
        ])
    }
}
