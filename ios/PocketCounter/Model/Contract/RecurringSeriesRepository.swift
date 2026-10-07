import Foundation

/// There is no `list()`: the app never lists series.
protocol RecurringSeriesRepository: Sendable {
    /// Resolve-or-create: a name already in use answers with the existing series.
    func create(_ draft: RecurringSeriesDraft) async throws(WriteFailure) -> RecurringSeries

    /// Rewrites the row's tags on the server.
    func link(_ id: TransactionID, to series: SeriesID) async throws(WriteFailure)

    /// `.vanished` means the series or the row is not there, or the row was not in it.
    func unlink(_ id: TransactionID, from series: SeriesID) async throws(WriteFailure)
}
