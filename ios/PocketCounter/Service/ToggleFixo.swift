import Foundation

/// Two calls to make a row fixo, one to make it plain. Creating is resolve-or-create on the
/// server, so repeating it after a failed link converges on the same recurring transaction: nothing to undo.
struct ToggleFixo: Sendable {
    let recurring: any RecurringTransactionRepository

    func toggle(_ item: HistoryItem) async throws(WriteFailure) {
        guard let recurringTransactionId = item.recurringTransactionId else {
            let created = try await recurring.create(.makingFixo(item))
            try await recurring.link(item.id, to: created.id)
            return
        }
        do {
            try await recurring.unlink(item.id, from: recurringTransactionId)
        } catch {
            // Already out of that recurring transaction: the reload is the answer. A link's 404 is not this.
            guard error == .vanished else { throw error }
        }
    }
}
