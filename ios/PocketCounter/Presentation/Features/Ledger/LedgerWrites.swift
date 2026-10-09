import Foundation

/// Every local fact the last server answer has not accounted for; `overlaying` projects them over it.
/// An in-flight fact needs no age; a settled or failed one does, so an older answer cannot retire it.
struct LedgerWrites: Equatable, Sendable {
    private(set) var statuses: [TransactionID: PaymentStatusWrite] = [:]
    /// Fixo and deletion. Only a settled deletion projects, by hiding its row.
    private(set) var intents: [TransactionID: RowIntentWrite] = [:]
    private(set) var reorders: [ReorderKey: ReorderWrite] = [:]
    private(set) var revision = 0
    private var newestFact: [RefYearMonth: Int] = [:]

    // Declared so the memberwise init cannot forge a registry no verb produces.
    init() {}

    /// One door per row across both maps.
    func isWriting(_ id: TransactionID) -> Bool {
        statuses[id]?.isInFlight == true || intents[id]?.isInFlight == true
    }

    func reorderFailure(in ref: RefYearMonth, kind: TransactionType) -> WriteFailure? {
        guard case .failed(let failure) = reorders[ReorderKey(ref: ref, kind: kind)]?.phase else { return nil }
        return failure
    }

    func overlaying(_ ledger: MonthLedger) -> MonthLedger {
        let ref = ledger.ref
        let removed = intents.filter { $0.value.ref == ref && $0.value.phase == .settled(.deletion) }
        let statuses = statuses.filter { $0.value.ref == ref }.compactMapValues(\.projection)
        let shown = removed.keys.reduce(ledger) { $0.removing($1) }.applying(statuses)
        return [TransactionType.expense, .income].reduce(shown) { current, kind in
            guard let write = reorders[ReorderKey(ref: ref, kind: kind)], write.isProjected else { return current }
            return current.reordering(write.order)
        }
    }

    func holdsFacts(newerThan revision: Int, in ref: RefYearMonth) -> Bool {
        (newestFact[ref] ?? 0) > revision
    }

    mutating func beginStatus(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
        statuses[id] = PaymentStatusWrite(ref: ref, phase: .inFlight(target))
    }

    mutating func settleStatus(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
        statuses[id] = PaymentStatusWrite(ref: ref, phase: .settled(target))
        recordFact(in: ref)
    }

    mutating func failStatus(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
        statuses[id] = PaymentStatusWrite(ref: ref, phase: .failed(failure))
        recordFact(in: ref)
    }

    mutating func dropStatus(_ id: TransactionID) {
        statuses[id] = nil
    }

    mutating func beginIntent(_ id: TransactionID, ref: RefYearMonth, target: RowIntent) {
        intents[id] = RowIntentWrite(ref: ref, phase: .inFlight(target))
    }

    /// A failed status write must not outlive its row.
    mutating func settleDeletion(_ id: TransactionID, ref: RefYearMonth) {
        intents[id] = RowIntentWrite(ref: ref, phase: .settled(.deletion))
        statuses[id] = nil
        recordFact(in: ref)
    }

    mutating func failIntent(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
        intents[id] = RowIntentWrite(ref: ref, phase: .failed(failure), attempted: intents[id]?.target)
        recordFact(in: ref)
    }

    mutating func dropIntent(_ id: TransactionID) {
        intents[id] = nil
    }

    /// A fresh attempt supersedes the old notice: the order on screen is the new one.
    mutating func recordReorder(_ key: ReorderKey, order: [TransactionID]) {
        reorders[key] = ReorderWrite(order: order, phase: .inFlight)
    }

    /// Only the drag that is still in flight settles: another drag's answer must not promote a
    /// failure, which carries no order and whose notice is the only account of it.
    mutating func settleReorder(_ key: ReorderKey) {
        guard case .inFlight = reorders[key]?.phase, let write = reorders[key] else { return }
        reorders[key] = ReorderWrite(order: write.order, phase: .settled)
        recordFact(in: key.ref)
    }

    mutating func failReorder(_ key: ReorderKey, _ failure: WriteFailure) {
        reorders[key] = ReorderWrite(order: [], phase: .failed(failure))
        recordFact(in: key.ref)
    }

    mutating func dropReorder(_ key: ReorderKey) {
        reorders[key] = nil
    }

    /// The server answered for `ref` as of `revision`. In-flight writes still stand, and so does
    /// the whole month if any fact is newer than the answer: the error is toward keeping.
    mutating func answered(for ref: RefYearMonth, at revision: Int) {
        guard !holdsFacts(newerThan: revision, in: ref) else { return }
        reorders = reorders.filter { $0.key.ref != ref || $0.value.phase == .inFlight }
        statuses = statuses.filter { Self.outlivesAnswer($0.value, for: ref) }
        intents = intents.filter { Self.outlivesAnswer($0.value, for: ref) }
    }

    /// Targets of the writes that landed, to announce to VoiceOver. A write dropped on
    /// `.sessionExpired` or retired by an answer is no longer settled, so it is not announced.
    static func completed(
        from old: [TransactionID: PaymentStatusWrite],
        to new: [TransactionID: PaymentStatusWrite]
    ) -> [PaymentStatus] {
        old.compactMap { id, write in
            guard write.isInFlight, case .settled(let target)? = new[id]?.phase else { return nil }
            return target
        }
    }

    private mutating func recordFact(in ref: RefYearMonth) {
        revision += 1
        newestFact[ref] = revision
    }

    private static func outlivesAnswer<Target>(_ write: RowWrite<Target>, for ref: RefYearMonth) -> Bool {
        write.ref != ref || write.isInFlight
    }
}
