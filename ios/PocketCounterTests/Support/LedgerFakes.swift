import Foundation

@testable import PocketCounter

/// Stands in for `LoadLedger.month`: answers per ref, records every call, and can hold calls open.
@MainActor
final class LedgerSourceFake {
    var ledgers: [RefYearMonth: MonthLedger] = [:]
    var failure: LoadFailure?
    var rendezvous: Rendezvous?
    private(set) var calls: [RefYearMonth] = []

    var action: LoadMonthAction {
        { [self] ref throws(LoadFailure) in try await answer(ref) }
    }

    private func answer(_ ref: RefYearMonth) async throws(LoadFailure) -> MonthLedger {
        calls.append(ref)
        let hold = rendezvous
        let failure = failure
        let ledger = ledgers[ref] ?? MonthLedger(ref: ref, items: [], lookups: .fixture())
        await hold?.arrive()
        if let failure { throw failure }
        return ledger
    }
}

@MainActor
final class ExpirySignal {
    private(set) var count = 0

    var action: SessionExpiredAction {
        { [self] in count += 1 }
    }
}

/// A write verb that records its inputs, answers as scripted and can hold calls open.
final class WriteProbe<Input: Sendable>: @unchecked Sendable {
    private let lock = NSLock()
    private var recorded: [Input] = []
    private var result: Result<Void, WriteFailure>
    private let rendezvous: Rendezvous?

    init(_ result: Result<Void, WriteFailure> = .success(()), rendezvous: Rendezvous? = nil) {
        self.result = result
        self.rendezvous = rendezvous
    }

    var calls: [Input] { lock.withLock { recorded } }

    /// Rescripts the answer for the calls that follow.
    func answer(_ result: Result<Void, WriteFailure>) { lock.withLock { self.result = result } }

    /// Bounded: a call that never comes fails on the assertion instead of hanging.
    func untilCalled(_ expected: Int = 1) async {
        var spins = 0
        while calls.count < expected, spins < 10_000 {
            await Task.yield()
            spins += 1
        }
    }

    func run(_ input: Input) async throws(WriteFailure) {
        lock.withLock { recorded.append(input) }
        await rendezvous?.arrive()
        try lock.withLock { result }.get()
    }
}
