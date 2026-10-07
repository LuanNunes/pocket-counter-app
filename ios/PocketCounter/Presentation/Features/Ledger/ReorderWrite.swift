struct ReorderKey: Hashable, Sendable {
    let ref: RefYearMonth
    let kind: TransactionType
}

/// A reorder belongs to no row, so it is not a `RowWrite`. It carries the dragged order,
/// which is projected over the ledger until an answer accounts for it.
/// A failed one projects nothing, so it keeps no order.
struct ReorderWrite: Equatable, Sendable {
    enum Phase: Equatable, Sendable {
        case inFlight
        case settled
        case failed(WriteFailure)
    }

    let order: [TransactionID]
    let phase: Phase

    var isProjected: Bool {
        switch phase {
        case .inFlight, .settled: true
        case .failed: false
        }
    }
}
