import Foundation

/// The server resolves a draft by name and type, so equal drafts are the same recurring transaction. A row with
/// no usable name drafts "Conta fixa", which gathers every such row of that type into one recurring transaction.
struct RecurringTransactionDraft: Equatable, Sendable {
    let name: String
    let type: TransactionType

    static func makingFixo(_ item: HistoryItem) -> RecurringTransactionDraft {
        RecurringTransactionDraft(name: name(for: item), type: item.type)
    }

    private static func name(for item: HistoryItem) -> String {
        item.hasTitle ? item.displayTitle() : "Conta fixa"
    }
}
