import Foundation

/// A write the ledger performs, or the reload that follows one. Kept out of `TransactionsAction`:
/// those are synchronous view-state transitions, and these are network calls.
enum LedgerCommand {
    case toggleStatus(HistoryItem)
    case toggleFixo(HistoryItem)
    case delete(HistoryItem)
    case refresh
    case move([TransactionID])
}
