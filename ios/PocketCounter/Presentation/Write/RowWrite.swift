import Foundation

/// One entry per row, so "in flight and failed" cannot be represented.
struct RowWrite<Target: Equatable & Sendable>: Equatable, Sendable {
    enum Phase: Equatable, Sendable {
        case inFlight(Target)
        case settled(Target)
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

    /// In flight only: the one-door rule and `isWriting` read this.
    var target: Target? {
        guard case .inFlight(let target) = phase else { return nil }
        return target
    }

    /// What the write asks the screen to show: in flight or landed.
    var projection: Target? {
        switch phase {
        case .inFlight(let target), .settled(let target): target
        case .failed: nil
        }
    }

    var isInFlight: Bool { target != nil }
}
