import Foundation

/// Two calls to make a row fixo, one to make it plain. Creating is resolve-or-create on the
/// server, so repeating it after a failed link converges on the same series: nothing to undo.
struct ToggleFixo: Sendable {
    let series: any RecurringSeriesRepository

    func toggle(_ item: HistoryItem) async throws(WriteFailure) {
        guard let seriesId = item.seriesId else {
            let created = try await series.create(.makingFixo(item))
            try await series.link(item.id, to: created.id)
            return
        }
        do {
            try await series.unlink(item.id, from: seriesId)
        } catch {
            // Already out of that series: the reload is the answer. A link's 404 is not this.
            guard error == .vanished else { throw error }
        }
    }
}
