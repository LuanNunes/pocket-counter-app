import Foundation

/// The server resolves a draft by name and type, so equal drafts are the same series. A row with
/// no usable name drafts "Conta fixa", which gathers every such row of that type into one series.
struct RecurringSeriesDraft: Equatable, Sendable {
    let name: String
    let type: TransactionType
    let recurrenceDay: Int

    static func makingFixo(_ item: HistoryItem) -> RecurringSeriesDraft {
        RecurringSeriesDraft(name: name(for: item), type: item.type, recurrenceDay: item.date.day)
    }

    private static func name(for item: HistoryItem) -> String {
        item.hasTitle ? item.displayTitle() : "Conta fixa"
    }
}
