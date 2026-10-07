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
        fileprivate(set) var months: [RefYearMonth: LoadState<MonthLedger>] = [:]
        /// What the user asked for, by row. Never written into `months`: that stays the server's answer.
        fileprivate(set) var writes: [TransactionID: PaymentStatusWrite] = [:]
        /// Fixo and deletion, by row. Read only by the control that started one: the ledger consequence is the server's.
        fileprivate(set) var intents: [TransactionID: RowIntentWrite] = [:]
        /// The last reorder the server refused. Not a row's: the list already shows the order it asked for.
        fileprivate(set) var failedReorder: FailedReorder?

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

        /// One door per row across both maps.
        func isWriting(_ id: TransactionID) -> Bool {
            writes[id]?.target != nil || intents[id]?.target != nil
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

        mutating func beginIntent(_ id: TransactionID, ref: RefYearMonth, target: RowIntent) {
            intents[id] = RowIntentWrite(ref: ref, phase: .inFlight(target))
        }

        mutating func failIntent(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
            intents[id] = RowIntentWrite(ref: ref, phase: .failed(failure), attempted: intents[id]?.target)
        }

        mutating func dropIntent(_ id: TransactionID) {
            intents[id] = nil
        }

        /// A failed status write can outlive its row (it fails, then the row is deleted), and no
        /// reload would ever clear it.
        mutating func completeDeletion(_ id: TransactionID, ref: RefYearMonth) {
            months[ref]?.amend { $0.removing(id) }
            intents[id] = nil
            writes[id] = nil
        }

        /// Projects into the committed ledger, like a deletion: no row owns a reorder.
        /// A fresh attempt supersedes the old notice: the order on screen is the new one.
        mutating func beginReorder(_ ref: RefYearMonth, order: [TransactionID]) {
            months[ref]?.amend { $0.reordering(order) }
            failedReorder = nil
        }

        mutating func revertReorder(_ ref: RefYearMonth, to order: [TransactionID]) {
            months[ref]?.amend { $0.reordering(order) }
        }

        mutating func failReorder(_ ref: RefYearMonth, kind: TransactionType, _ failure: WriteFailure) {
            failedReorder = FailedReorder(ref: ref, kind: kind, failure: failure)
        }

        mutating func dropReorder() {
            failedReorder = nil
        }

        /// The server just answered for `ref`, so its failed writes are stale. In-flight ones still stand.
        mutating func commit(_ ledger: MonthLedger, for ref: RefYearMonth) {
            months[ref, default: LoadState()].commit(ledger)
            if failedReorder?.ref == ref { failedReorder = nil }
            writes = writes.filter { Self.outlivesAnswer($0.value, for: ref) }
            intents = intents.filter { Self.outlivesAnswer($0.value, for: ref) }
        }

        private static func outlivesAnswer<Target>(_ write: RowWrite<Target>, for ref: RefYearMonth) -> Bool {
            write.ref != ref || write.target != nil
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

    /// Takes the row as displayed. Async and spawning no `Task`, like `delete`.
    func toggleFixo(of item: HistoryItem) async {
        guard state.holdsCommittedItem(item.id, in: item.ref) else { return }
        guard !state.isWriting(item.id) else { return }
        state.beginIntent(item.id, ref: item.ref, target: .fixo(!item.isFixo))
        do {
            try await changeFixo(item)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropIntent(item.id)
                await onSessionExpired()
            case .authenticationUnavailable, .unreachable, .vanished, .rejected, .server:
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
        guard state.holdsCommittedItem(item.id, in: item.ref) else { return }
        guard !state.isWriting(item.id) else { return }
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
            case .authenticationUnavailable, .unreachable, .rejected, .server:
                state.failIntent(item.id, ref: item.ref, error)
                return
            }
        }
        state.completeDeletion(item.id, ref: item.ref)
    }

    /// `group` is the rows as dragged, in their new order. Async and spawning no `Task`, like `delete`.
    /// Does not consult `isWriting`: a reorder belongs to no row, and `displayOrder` is disjoint from every row write.
    func reorder(_ group: [TransactionID], of kind: TransactionType, in ref: RefYearMonth) async {
        guard let ledger = state.months[ref]?.value else { return }
        let all = ledger.items.filter { $0.type == kind }.map(\.id)
        let order = LedgerReorder.placing(group, into: all)
        guard order != all else { return }
        // A commit from a load already in flight can overwrite this; the next load reconciles.
        state.beginReorder(ref, order: order)
        do {
            try await reorderTransactions(order)
        } catch {
            switch error {
            case .sessionExpired:
                state.dropReorder()
                await onSessionExpired()
                return
            case .authenticationUnavailable, .unreachable, .vanished, .rejected, .server:
                break
            }
            // A partial reorder may have committed; the reload is the only way to see how much.
            // Recorded after it: the reload's own answer would clear the notice.
            if state.month == ref { await refresh() } else { state.revertReorder(ref, to: all) }
            state.failReorder(ref, kind: kind, error)
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
