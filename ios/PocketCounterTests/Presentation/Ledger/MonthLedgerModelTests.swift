import Testing

@testable import PocketCounter

/// Stands in for `LoadLedger.month`: records refs, answers as scripted, and can hold calls open.
@MainActor
private final class LoadMonthRecorder {
    private(set) var calls: [RefYearMonth] = []
    var outcomes: [Result<MonthLedger, LoadFailure>] = []
    private var holding = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func hold() { holding = true }

    func untilSuspended(_ count: Int = 1) async {
        while waiters.count < count { await Task.yield() }
    }

    func release(_ index: Int) {
        waiters[index].resume()
    }

    var action: LoadMonthAction {
        { [self] ref throws(LoadFailure) in
            let outcome = await begin(ref)
            return try outcome.get()
        }
    }

    private func begin(_ ref: RefYearMonth) async -> Result<MonthLedger, LoadFailure> {
        calls.append(ref)
        let outcome = outcomes.isEmpty ? .success(MonthLedger.fixture(ref)) : outcomes.removeFirst()
        guard holding else { return outcome }
        await withCheckedContinuation { waiters.append($0) }
        return outcome
    }
}

@MainActor
private final class ExpiryCounter {
    private(set) var count = 0

    var action: SessionExpiredAction {
        { [self] in count += 1 }
    }
}

extension MonthLedger {
    fileprivate static func fixture(_ ref: RefYearMonth, items: [HistoryItem] = []) -> MonthLedger {
        MonthLedger(ref: ref, items: items, lookups: LookupSet(categories: [], tags: [], cards: []))
    }
}

@MainActor
@Suite("MonthLedgerModel")
struct MonthLedgerModelTests {

    private let recorder = LoadMonthRecorder()
    private let expiry = ExpiryCounter()

    private func ref(_ year: Int, _ month: Int) throws -> RefYearMonth {
        try RefYearMonth(year: year, month: month)
    }

    private func model(at month: RefYearMonth? = nil, outcomes: [Result<MonthLedger, LoadFailure>] = []) throws -> MonthLedgerModel {
        recorder.outcomes = outcomes
        let october = try ref(2026, 10)
        return MonthLedgerModel(
            window: .around(october), month: month ?? october,
            loadMonth: recorder.action, setPaymentStatus: { _, _ in }, onSessionExpired: expiry.action
        )
    }

    @Test("a first load shows the month's ledger")
    func firstLoad() async throws {
        let october = try ref(2026, 10)
        let model = try model()

        await model.load()

        #expect(model.state.load.phase == .loaded(.fixture(october)))
        #expect(recorder.calls == [october])
    }

    @Test("an empty month is loaded, not failed")
    func emptyMonth() async throws {
        let model = try model()

        await model.load()

        guard case .loaded(let ledger) = model.state.load.phase else {
            Issue.record("expected .loaded, got \(model.state.load.phase)")
            return
        }
        #expect(ledger.items.isEmpty)
    }

    @Test("a failed first load carries no ledger")
    func failedFirstLoad() async throws {
        let model = try model(outcomes: [.failure(.unreachable)])

        await model.load()

        #expect(model.state.load.phase == .failed(.unreachable))
        #expect(!model.state.load.isLoading)
    }

    @Test("a failed reload keeps the ledger it had")
    func failedReload() async throws {
        let october = try ref(2026, 10)
        let model = try model(outcomes: [.success(.fixture(october)), .failure(.server)])
        await model.load()

        await model.refresh()

        #expect(model.state.load.phase == .stale(.fixture(october), .server))
    }

    @Test("a cancelled load records neither a value nor a failure and stops loading")
    func abandoned() async throws {
        let model = try model(outcomes: [.failure(.abandoned)])

        await model.load()

        #expect(model.state.load == LoadState())
    }

    @Test("an expired session writes nothing and signals once")
    func sessionExpired() async throws {
        let model = try model(outcomes: [.failure(.sessionExpired)])

        await model.load()

        #expect(model.state.load == LoadState())
        #expect(expiry.count == 1)
    }

    @Test("selecting a month outside the window clamps to its edge")
    func selectClamps() throws {
        let model = try model()

        model.select(try ref(2030, 1))
        #expect(model.state.month == (try ref(2027, 12)))

        model.select(try ref(2000, 1))
        #expect(model.state.month == (try ref(2025, 1)))
    }

    @Test("the previous and next buttons are disabled at the window's edges")
    func bounds() throws {
        let model = try model(at: try ref(2025, 1))
        #expect(!model.state.canSelectPrevious)
        #expect(model.state.canSelectNext)

        model.select(try ref(2027, 12))
        #expect(model.state.canSelectPrevious)
        #expect(!model.state.canSelectNext)
    }

    @Test("previous and next step one month")
    func stepping() throws {
        let model = try model(at: try ref(2026, 12))

        model.selectNext()
        #expect(model.state.month == (try ref(2027, 1)))

        model.selectPrevious()
        model.selectPrevious()
        #expect(model.state.month == (try ref(2026, 11)))
    }

    @Test("returning to a loaded month issues no new request")
    func revisit() async throws {
        let october = try ref(2026, 10)
        let model = try model()
        await model.load()
        model.select(try ref(2026, 11))
        await model.load()

        model.select(october)
        await model.load()

        #expect(recorder.calls == [october, try ref(2026, 11)])
        #expect(model.state.load.phase == .loaded(.fixture(october)))
    }

    @Test("a refresh always requests, even over a loaded month")
    func refreshAlwaysRequests() async throws {
        let model = try model()
        await model.load()

        await model.refresh()

        #expect(recorder.calls.count == 2)
    }

    @Test("a load while one is in flight does not ask twice")
    func loadWhileLoading() async throws {
        let model = try model()
        recorder.hold()
        let first = Task { await model.load() }
        await recorder.untilSuspended()

        await model.load()
        recorder.release(0)
        await first.value

        #expect(recorder.calls.count == 1)
    }

    @Test("changing month drops the in-flight load: the new month shows nothing of the old")
    func monthChangeCancels() async throws {
        let model = try model()
        recorder.hold()
        let task = Task { await model.load() }
        await recorder.untilSuspended()

        model.select(try ref(2026, 11))

        #expect(model.state.load == LoadState())
        recorder.release(0)
        await task.value
        #expect(model.state.load == LoadState())
    }

    @Test("a late response to a request that a month change cancelled is dropped, not filed anywhere")
    func lateResponse() async throws {
        let october = try ref(2026, 10)
        let model = try model()
        recorder.hold()
        let task = Task { await model.load() }
        await recorder.untilSuspended()
        model.select(try ref(2026, 11))

        recorder.release(0)
        await task.value

        #expect(model.state.load.phase == .firstLoad)
        model.select(october)
        #expect(model.state.load.phase == .firstLoad)
    }

    @Test("a response whose ref disagrees with the request fails the request and is filed nowhere")
    func disagreeingRef() async throws {
        let november = try ref(2026, 11)
        let model = try model(outcomes: [.success(.fixture(november))])

        await model.load()

        #expect(model.state.load.phase == .failed(.server))
        model.select(november)
        #expect(model.state.load.phase == .firstLoad)
    }

    @Test("an older response for a month never overwrites a newer one")
    func olderResponseLosesToNewer() async throws {
        let october = try ref(2026, 10)
        let older = MonthLedger.fixture(october, items: [.expense(1)])
        let newer = MonthLedger.fixture(october, items: [.expense(2)])
        let model = try model(outcomes: [.success(older), .success(newer)])
        recorder.hold()
        let first = Task { await model.load() }
        await recorder.untilSuspended(1)
        let second = Task { await model.refresh() }
        await recorder.untilSuspended(2)

        recorder.release(1)
        await second.value
        recorder.release(0)
        await first.value

        #expect(model.state.load.phase == .loaded(newer))
    }

    @Test("cancelling stops the loading overlay and keeps what was there")
    func cancel() async throws {
        let model = try model()
        recorder.hold()
        let task = Task { await model.load() }
        await recorder.untilSuspended()

        model.cancel()

        #expect(model.state.load == LoadState())
        recorder.release(0)
        await task.value
        #expect(model.state.load == LoadState())
    }

    @Test("a superseded expired session still signals but leaves the newer request's loading alone")
    func supersededSessionExpired() async throws {
        let october = try ref(2026, 10)
        let model = try model(outcomes: [.failure(.sessionExpired), .success(.fixture(october))])
        recorder.hold()
        let first = Task { await model.load() }
        await recorder.untilSuspended(1)
        let second = Task { await model.refresh() }
        await recorder.untilSuspended(2)

        recorder.release(0)
        await first.value

        #expect(expiry.count == 1)
        #expect(model.state.load.isLoading)
        recorder.release(1)
        await second.value
        #expect(model.state.load.phase == .loaded(.fixture(october)))
    }
}
