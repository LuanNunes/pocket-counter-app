import Foundation

@testable import PocketCounter

struct FakeRecurringSeriesRepository: RecurringSeriesRepository {
    enum Call: Equatable {
        case create(RecurringSeriesDraft)
        case link(TransactionID, SeriesID)
        case unlink(TransactionID, SeriesID)
    }

    var createResult: Result<RecurringSeries, WriteFailure> = .success(
        RecurringSeries(id: SeriesID(rawValue: "created"), name: "Aluguel", type: .expense, recurrenceDay: 5))
    var linkResult: Result<Void, WriteFailure> = .success(())
    var unlinkResult: Result<Void, WriteFailure> = .success(())
    var log = CallLog()

    func create(_ draft: RecurringSeriesDraft) async throws(WriteFailure) -> RecurringSeries {
        log.record(.create(draft))
        return try createResult.get()
    }

    func link(_ id: TransactionID, to series: SeriesID) async throws(WriteFailure) {
        log.record(.link(id, series))
        try linkResult.get()
    }

    func unlink(_ id: TransactionID, from series: SeriesID) async throws(WriteFailure) {
        log.record(.unlink(id, series))
        try unlinkResult.get()
    }

    final class CallLog: @unchecked Sendable {
        private let lock = NSLock()
        private var recorded: [Call] = []

        var calls: [Call] { lock.withLock { recorded } }

        func record(_ call: Call) { lock.withLock { recorded.append(call) } }
    }
}
