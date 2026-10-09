import Observation

typealias LoadMonthAction = @Sendable (RefYearMonth) async throws(LoadFailure) -> MonthLedger

typealias SetPaymentStatusAction = @Sendable (TransactionID, PaymentStatus) async throws(WriteFailure) -> Void

typealias ToggleFixoAction = @Sendable (HistoryItem) async throws(WriteFailure) -> Void

typealias DeleteTransactionAction = @Sendable (TransactionID) async throws(WriteFailure) -> Void

typealias ReorderTransactionsAction = @Sendable ([TransactionID]) async throws(WriteFailure) -> Void

@MainActor
@Observable
final class MonthLedgerModel {
    struct State: Equatable {
        let window: MonthWindow
        var month: RefYearMonth
        private(set) var months: [RefYearMonth: LoadState<MonthLedger>] = [:]
        fileprivate(set) var writes = LedgerWrites()

        var load: LoadState<MonthLedger> {
            var load = months[month] ?? LoadState()
            load.amend { writes.overlaying($0) }
            return load
        }

        var canSelectPrevious: Bool { window.contains(month.previous()) }
        var canSelectNext: Bool { window.contains(month.next()) }

        fileprivate init(window: MonthWindow, month: RefYearMonth) {
            self.window = window
            self.month = month
        }

        /// `months` holds only what the server said; this is what the screen shows.
        func ledger(for ref: RefYearMonth) -> MonthLedger? {
            months[ref]?.value.map(writes.overlaying)
        }

        func holdsRow(_ id: TransactionID, in ref: RefYearMonth) -> Bool {
            ledger(for: ref)?.items.contains { $0.id == id } ?? false
        }

        mutating func beginStatus(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
            writes.beginStatus(id, ref: ref, target: target)
        }

        mutating func completeWrite(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
            writes.settleStatus(id, ref: ref, target: target)
        }

        mutating func failStatus(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
            writes.failStatus(id, ref: ref, failure)
        }

        mutating func dropStatus(_ id: TransactionID) {
            writes.dropStatus(id)
        }

        mutating func beginIntent(_ id: TransactionID, ref: RefYearMonth, target: RowIntent) {
            writes.beginIntent(id, ref: ref, target: target)
        }

        mutating func failIntent(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
            writes.failIntent(id, ref: ref, failure)
        }

        mutating func dropIntent(_ id: TransactionID) {
            writes.dropIntent(id)
        }

        mutating func completeDeletion(_ id: TransactionID, ref: RefYearMonth) {
            writes.settleDeletion(id, ref: ref)
        }

        mutating func beginReorder(_ ref: RefYearMonth, kind: TransactionType, order: [TransactionID]) {
            writes.recordReorder(ReorderKey(ref: ref, kind: kind), order: order)
        }

        mutating func settleReorder(_ ref: RefYearMonth, kind: TransactionType) {
            writes.settleReorder(ReorderKey(ref: ref, kind: kind))
        }

        mutating func failReorder(_ ref: RefYearMonth, kind: TransactionType, _ failure: WriteFailure) {
            writes.failReorder(ReorderKey(ref: ref, kind: kind), failure)
        }

        mutating func dropReorder(_ ref: RefYearMonth, kind: TransactionType) {
            writes.dropReorder(ReorderKey(ref: ref, kind: kind))
        }

        /// Keeps the displayed month, which the caller refreshes at once: no flash of skeleton.
        mutating func dropMonths(except ref: RefYearMonth) {
            months = months.filter { $0.key == ref }
        }

        mutating func beginLoading(_ ref: RefYearMonth) {
            months[ref, default: LoadState()].beginLoading()
        }

        mutating func fail(_ failure: LoadFailure, for ref: RefYearMonth) {
            months[ref, default: LoadState()].fail(failure)
        }

        mutating func abandon(_ ref: RefYearMonth) {
            months[ref, default: LoadState()].abandon()
        }

        /// The only way a ledger enters `months`. `revision` is what `writes` held when the request was issued.
        mutating func commit(_ ledger: MonthLedger, for ref: RefYearMonth, at revision: Int) {
            months[ref, default: LoadState()].commit(ledger)
            writes.answered(for: ref, at: revision)
        }
    }

    private(set) var state: State
    private let loadMonth: LoadMonthAction
    private let setPaymentStatus: SetPaymentStatusAction
    private let changeFixo: ToggleFixoAction
    private let deleteTransaction: DeleteTransactionAction
    private let reorderTransactions: ReorderTransactionsAction
    private let onSessionExpired: SessionExpiredAction

    init(
        window: MonthWindow = .around(.current),
        month: RefYearMonth = .current,
        loadMonth: @escaping LoadMonthAction,
        setPaymentStatus: @escaping SetPaymentStatusAction,
        toggleFixo: @escaping ToggleFixoAction,
        deleteTransaction: @escaping DeleteTransactionAction,
        reorderTransactions: @escaping ReorderTransactionsAction,
        onSessionExpired: @escaping SessionExpiredAction
    ) {
        state = State(window: window, month: window.clamped(month))
        self.loadMonth = loadMonth
        self.setPaymentStatus = setPaymentStatus
        changeFixo = toggleFixo
        self.deleteTransaction = deleteTransaction
        self.reorderTransactions = reorderTransactions
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

    /// For a write whose month the client cannot know (a card charge files under the statement's
    /// month, which the server decides): every other cached month is stale, and `load()` never refetches one.
    func invalidateAndRefresh() async {
        stopInFlight()
        state.dropMonths(except: state.month)
        await refresh()
    }

    func cancel() {
        stopInFlight()
    }

    /// Takes the row as displayed, so the target is the opposite of what the user sees.
    /// Async and spawning no `Task`: the view wraps it.
    func togglePaymentStatus(of item: HistoryItem) async {
        // A redacted placeholder row carries the real handler; this stops its tap.
        guard state.holdsRow(item.id, in: item.ref) else { return }
        guard !state.writes.isWriting(item.id) else { return }
        let target: PaymentStatus = item.statusPayment == .paid ? .pending : .paid
        state.beginStatus(item.id, ref: item.ref, target: target)
        do {
            try await setPaymentStatus(item.id, target)
            state.completeWrite(item.id, ref: item.ref, target: target)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropStatus(item.id)
                await onSessionExpired()
            case .authenticationUnavailable, .unreachable, .vanished, .rejected, .duplicate, .server:
                state.failStatus(item.id, ref: item.ref, error)
            }
        }
    }

    /// Takes the row as displayed. Async and spawning no `Task`, like `delete`.
    func toggleFixo(of item: HistoryItem) async {
        guard state.holdsRow(item.id, in: item.ref) else { return }
        guard !state.writes.isWriting(item.id) else { return }
        state.beginIntent(item.id, ref: item.ref, target: .fixo(!item.isFixo))
        do {
            try await changeFixo(item)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropIntent(item.id)
                await onSessionExpired()
            case .authenticationUnavailable, .unreachable, .vanished, .rejected, .duplicate, .server:
                state.failIntent(item.id, ref: item.ref, error)
            }
            return
        }
        // `refresh()` reloads the month on screen, whichever month the write touched.
        guard state.month == item.ref else { state.dropIntent(item.id); return }
        await refresh()
        // Dropped even if the reload failed: the month is then stale and says so, and the switch
        // returns to the committed value. The one place the display can lag the server.
        state.dropIntent(item.id)
    }

    /// Async and spawning no `Task`: the view wraps it, so dismissing the sheet cannot cancel it.
    func delete(_ item: HistoryItem) async {
        guard state.holdsRow(item.id, in: item.ref) else { return }
        guard !state.writes.isWriting(item.id) else { return }
        state.beginIntent(item.id, ref: item.ref, target: .deletion)
        do {
            try await deleteTransaction(item.id)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropIntent(item.id)
                await onSessionExpired()
                return
            case .vanished:
                break // DELETE is idempotent: a row already gone is the outcome the user asked for.
            case .authenticationUnavailable, .unreachable, .rejected, .duplicate, .server:
                state.failIntent(item.id, ref: item.ref, error)
                return
            }
        }
        state.completeDeletion(item.id, ref: item.ref)
    }

    /// `group` is the rows as dragged, in their new order. Async and spawning no `Task`, like `delete`.
    /// Does not consult `isWriting`: a reorder belongs to no row, and `displayOrder` is disjoint from every row write.
    func reorder(_ group: [TransactionID], of kind: TransactionType, in ref: RefYearMonth) async {
        guard let ledger = state.ledger(for: ref) else { return }
        let all = ledger.items.filter { $0.type == kind }.map(\.id)
        let order = LedgerReorder.placing(group, into: all)
        guard order != all else { return }
        state.beginReorder(ref, kind: kind, order: order)
        do {
            try await reorderTransactions(order)
            state.settleReorder(ref, kind: kind)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropReorder(ref, kind: kind)
                await onSessionExpired()
                return
            case .authenticationUnavailable, .unreachable, .vanished, .rejected, .duplicate, .server:
                break
            }
            // A partial reorder may have committed; the reload is the only way to see how much.
            // Recorded after it: the reload's own answer would clear the notice.
            state.dropReorder(ref, kind: kind)
            if state.month == ref { await refresh() }
            state.failReorder(ref, kind: kind, error)
        }
    }

    private func request() async {
        let ref = state.month
        stopInFlight()
        state.beginLoading(ref)
        requestCount += 1
        let request = requestCount
        let answered = state.writes.revision
        latestRequest[ref] = request
        let task = Task { await perform(ref, request: request, answering: answered) }
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
        state.abandon(current.ref)
    }

    /// Writes by the month that was asked for, never by `state.month`, which may have moved on.
    /// A superseded request for the same month writes nothing.
    private func perform(_ ref: RefYearMonth, request: Int, answering revision: Int) async {
        do {
            let ledger = try await loadMonth(ref)
            guard latestRequest[ref] == request else { return }
            guard ledger.ref == ref else {
                state.fail(.server, for: ref)
                return
            }
            state.commit(ledger, for: ref, at: revision)
        } catch {
            let isLatest = latestRequest[ref] == request
            switch error {
            case .sessionExpired:
                if isLatest { state.abandon(ref) }
                await onSessionExpired()
            case .abandoned, .authenticationUnavailable, .unreachable, .notFound, .rejected, .server:
                guard isLatest else { return }
                state.fail(error, for: ref)
            }
        }
    }
}
