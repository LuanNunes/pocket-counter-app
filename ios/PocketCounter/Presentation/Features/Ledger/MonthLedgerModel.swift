import Observation

typealias LoadMonthAction = @Sendable (RefYearMonth) async throws(LoadFailure) -> MonthLedger

@MainActor
@Observable
final class MonthLedgerModel {
    struct State: Equatable {
        let window: MonthWindow
        var month: RefYearMonth
        fileprivate var months: [RefYearMonth: LoadState<MonthLedger>] = [:]

        var load: LoadState<MonthLedger> { months[month] ?? LoadState() }
        var canSelectPrevious: Bool { window.contains(month.previous()) }
        var canSelectNext: Bool { window.contains(month.next()) }

        fileprivate init(window: MonthWindow, month: RefYearMonth) {
            self.window = window
            self.month = month
        }
    }

    private(set) var state: State
    private let loadMonth: LoadMonthAction
    private let onSessionExpired: SessionExpiredAction

    init(
        window: MonthWindow = .around(.current),
        month: RefYearMonth = .current,
        loadMonth: @escaping LoadMonthAction,
        onSessionExpired: @escaping SessionExpiredAction
    ) {
        state = State(window: window, month: window.clamped(month))
        self.loadMonth = loadMonth
        self.onSessionExpired = onSessionExpired
    }

    private var requestCount = 0
    private var latestRequest: [RefYearMonth: Int] = [:]
    private var inFlight: (ref: RefYearMonth, task: Task<Void, Never>)?

    /// Does not load: the view's `.task(id: state.month)` does. A screen showing `MonthPill`
    /// without that task leaves the new month in `.firstLoad`.
    /// Clamps rather than guards: a month outside the window is the nearest edge, not a no-op.
    func select(_ month: RefYearMonth) {
        let month = state.window.clamped(month)
        guard month != state.month else { return }
        stopInFlight()
        state.month = month
    }

    func selectPrevious() {
        select(state.month.previous())
    }

    func selectNext() {
        select(state.month.next())
    }

    /// No-op when the month has a value or a load is in flight.
    func load() async {
        guard state.load.value == nil, !state.load.isLoading else { return }
        await request()
    }

    /// Always asks; the retry button and pull-to-refresh both land here.
    func refresh() async {
        await request()
    }

    func cancel() {
        stopInFlight()
    }

    private func request() async {
        let ref = state.month
        stopInFlight()
        state.months[ref, default: LoadState()].beginLoading()
        requestCount += 1
        let request = requestCount
        latestRequest[ref] = request
        let task = Task { await perform(ref, request: request) }
        inFlight = (ref, task)
        await withTaskCancellationHandler { await task.value } onCancel: { task.cancel() }
        guard inFlight?.task == task else { return }
        inFlight = nil
    }

    private func stopInFlight() {
        guard let current = inFlight else { return }
        inFlight = nil
        current.task.cancel()
        latestRequest[current.ref] = nil
        state.months[current.ref]?.abandon()
    }

    /// Writes by the month that was asked for, never by `state.month`, which may have moved on.
    /// A superseded request for the same month writes nothing.
    private func perform(_ ref: RefYearMonth, request: Int) async {
        do {
            let ledger = try await loadMonth(ref)
            guard latestRequest[ref] == request else { return }
            guard ledger.ref == ref else {
                state.months[ref, default: LoadState()].fail(.server)
                return
            }
            state.months[ref, default: LoadState()].commit(ledger)
        } catch {
            let isLatest = latestRequest[ref] == request
            switch error {
            case .sessionExpired:
                if isLatest { state.months[ref, default: LoadState()].abandon() }
                await onSessionExpired()
            case .abandoned, .authenticationUnavailable, .unreachable, .notFound, .rejected, .server:
                guard isLatest else { return }
                state.months[ref, default: LoadState()].fail(error)
            }
        }
    }
}
