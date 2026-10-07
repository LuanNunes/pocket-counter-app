import Foundation

/// One entry per row, so "in flight and failed" cannot be represented.
struct RowWrite<Target: Equatable & Sendable>: Equatable, Sendable {
    enum Phase: Equatable, Sendable {
        case inFlight(Target)
        case failed(WriteFailure)
    }

    let ref: RefYearMonth
    let phase: Phase
    /// What a failed write was asking for, so its notice can be titled by it.
    let attempted: Target?

    init(ref: RefYearMonth, phase: Phase, attempted: Target? = nil) {
        self.ref = ref
        self.phase = phase
        self.attempted = attempted
    }

    var target: Target? {
        guard case .inFlight(let target) = phase else { return nil }
        return target
    }
}
