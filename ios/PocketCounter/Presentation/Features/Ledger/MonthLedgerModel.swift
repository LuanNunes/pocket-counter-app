import Observation

typealias LoadMonthAction = @Sendable (RefYearMonth) async throws(LoadFailure) -> MonthLedger

typealias SetPaymentStatusAction = @Sendable (TransactionID, PaymentStatus) async throws(WriteFailure) -> Void

@MainActor
@Observable
final class MonthLedgerModel {
    struct State: Equatable {
        let window: MonthWindow
        var month: RefYearMonth
        fileprivate(set) var months: [RefYearMonth: LoadState<MonthLedger>] = [:]
        /// What the user asked for, by row. Never written into `months`: that stays the server's answer.
        fileprivate(set) var writes: [TransactionID: PaymentStatusWrite] = [:]

        var load: LoadState<MonthLedger> {
            var load = months[month] ?? LoadState()
            let targets = writes.compactMapValues(\.target)
            load.amend { $0.applying(targets) }
            return load
        }

        var canSelectPrevious: Bool { window.contains(month.previous()) }
        var canSelectNext: Bool { window.contains(month.next()) }

        fileprivate init(window: MonthWindow, month: RefYearMonth) {
            self.window = window
            self.month = month
        }

        func isWriting(_ id: TransactionID) -> Bool {
            writes[id]?.target != nil
        }

        func holdsCommittedItem(_ id: TransactionID, in ref: RefYearMonth) -> Bool {
            months[ref]?.value?.items.contains { $0.id == id } ?? false
        }

        mutating func beginWrite(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
            writes[id] = PaymentStatusWrite(ref: ref, phase: .inFlight(target))
        }

        /// Promotes and clears in one mutation, so no render sees the overlay gone and the ledger not yet updated.
        mutating func completeWrite(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
            months[ref]?.amend { $0.applying([id: target]) }
            writes[id] = nil
        }

        mutating func failWrite(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
            writes[id] = PaymentStatusWrite(ref: ref, phase: .failed(failure))
        }

        mutating func dropWrite(_ id: TransactionID) {
            writes[id] = nil
        }

        /// The server just answered for `ref`, so its failed writes are stale. In-flight ones still stand.
        mutating func commit(_ ledger: MonthLedger, for ref: RefYearMonth) {
            months[ref, default: LoadState()].commit(ledger)
            writes = writes.filter { $0.value.ref != ref || $0.value.target != nil }
        }
    }

    private(set) var state: State
    private let loadMonth: LoadMonthAction
    private let setPaymentStatus: SetPaymentStatusAction
    private let onSessionExpired: SessionExpiredAction

    init(
        window: MonthWindow = .around(.current),
        month: RefYearMonth = .current,
        loadMonth: @escaping LoadMonthAction,
        setPaymentStatus: @escaping SetPaymentStatusAction,
        onSessionExpired: @escaping SessionExpiredAction
    ) {
        state = State(window: window, month: window.clamped(month))
        self.loadMonth = loadMonth
        self.setPaymentStatus = setPaymentStatus
        self.onSessionExpired = onSessionExpired
    }

    private var requestCount = 0
    private var latestRequest: [RefYearMonth: Int] = [:]
    private var inFlight: (ref: RefYearMonth, task: Task<Void, Never>)?

    /// Does not load: `AppShell`'s `.task(id: state.month)` does. Only that one task may drive
    /// it: a per-screen task is cancelled by a tab switch, and cancelling aborts the load.
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

    var refreshAction: @Sendable () async -> Void { { [self] in await refresh() } }

    /// Always asks; the retry button and pull-to-refresh both land here.
    func refresh() async {
        await request()
    }

    func cancel() {
        stopInFlight()
    }

    /// Takes the row as displayed, so the target is the opposite of what the user sees.
    /// Async and spawning no `Task`: the view wraps it.
    func togglePaymentStatus(of item: HistoryItem) async {
        // A redacted placeholder row carries the real handler; this stops its tap.
        guard state.holdsCommittedItem(item.id, in: item.ref) else { return }
        guard !state.isWriting(item.id) else { return }
        let target: PaymentStatus = item.statusPayment == .paid ? .pending : .paid
        state.beginWrite(item.id, ref: item.ref, target: target)
        do {
            try await setPaymentStatus(item.id, target)
            state.completeWrite(item.id, ref: item.ref, target: target)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropWrite(item.id)
                await onSessionExpired()
            case .authenticationUnavailable, .unreachable, .vanished, .rejected, .server:
                state.failWrite(item.id, ref: item.ref, error)
            }
        }
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
            state.commit(ledger, for: ref)
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
