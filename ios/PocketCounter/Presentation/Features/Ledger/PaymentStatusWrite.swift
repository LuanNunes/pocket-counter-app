import Foundation

/// One entry per row, so "in flight and failed" cannot be represented.
struct PaymentStatusWrite: Equatable, Sendable {
    enum Phase: Equatable, Sendable {
        case inFlight(PaymentStatus)
        case failed(WriteFailure)
    }

    let ref: RefYearMonth
    let phase: Phase

    var target: PaymentStatus? {
        guard case .inFlight(let status) = phase else { return nil }
        return status
    }
}
