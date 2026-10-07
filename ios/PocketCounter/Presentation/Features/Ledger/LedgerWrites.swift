import Foundation

/// What the user asked for and the server has not settled. Never written into the committed ledger.
struct LedgerWrites: Equatable, Sendable {
    private(set) var statuses: [TransactionID: PaymentStatusWrite] = [:]
    /// Fixo and deletion. Read only by the control that started one: the ledger consequence is the server's.
    private(set) var intents: [TransactionID: RowIntentWrite] = [:]
    /// The last reorder the server refused.
    private(set) var failedReorder: FailedReorder? = nil

    // Declared so the memberwise init cannot forge a registry no verb produces.
    init() {}

    /// One door per row across both maps.
    func isWriting(_ id: TransactionID) -> Bool {
        statuses[id]?.target != nil || intents[id]?.target != nil
    }

    func overlaying(_ ledger: MonthLedger) -> MonthLedger {
        ledger.applying(statuses.compactMapValues(\.target))
    }

    mutating func beginStatus(_ id: TransactionID, ref: RefYearMonth, target: PaymentStatus) {
        statuses[id] = PaymentStatusWrite(ref: ref, phase: .inFlight(target))
    }

    mutating func failStatus(_ id: TransactionID, ref: RefYearMonth, _ failure: WriteFailure) {
        statuses[id] = PaymentStatusWrite(ref: ref, phase: .failed(failure))
    }

    mutating func dropStatus(_ id: TransactionID) {
        statuses[id] = nil
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

    /// A failed status write can outlive its row, and no reload would ever clear it.
    mutating func forget(_ id: TransactionID) {
        statuses[id] = nil
        intents[id] = nil
    }

    mutating func failReorder(_ ref: RefYearMonth, kind: TransactionType, _ failure: WriteFailure) {
        failedReorder = FailedReorder(ref: ref, kind: kind, failure: failure)
    }

    mutating func dropReorder() {
        failedReorder = nil
    }

    /// The server just answered for `ref`, so its failed writes are stale. In-flight ones still stand.
    mutating func answered(for ref: RefYearMonth) {
        if failedReorder?.ref == ref { failedReorder = nil }
        statuses = statuses.filter { Self.outlivesAnswer($0.value, for: ref) }
        intents = intents.filter { Self.outlivesAnswer($0.value, for: ref) }
    }

    /// Targets of the writes that landed, to announce to VoiceOver. An entry also leaves the map
    /// when a write is dropped on `.sessionExpired`, so absence alone would announce a save that
    /// never happened: the row must actually show the target.
    static func completed(
        from old: [TransactionID: PaymentStatusWrite],
        to new: [TransactionID: PaymentStatusWrite],
        statusOf: (TransactionID) -> PaymentStatus?
    ) -> [PaymentStatus] {
        old.compactMap { id, write in
            guard new[id] == nil, let target = write.target, statusOf(id) == target else { return nil }
            return target
        }
    }

    private static func outlivesAnswer<Target>(_ write: RowWrite<Target>, for ref: RefYearMonth) -> Bool {
        write.ref != ref || write.target != nil
    }
}
