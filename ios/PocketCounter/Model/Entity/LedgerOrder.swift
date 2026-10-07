import Foundation

/// The one total order for ledger rows. The sort is unstable and `displayOrder` is 0 on most rows.
enum LedgerOrder {
    static func key(_ item: HistoryItem) -> (Int, Int, Int, CalendarDay, String) {
        (item.ref.raw, item.type == .income ? 0 : 1, item.displayOrder, item.date, item.id.rawValue)
    }

    static func sorted(_ items: [HistoryItem]) -> [HistoryItem] {
        items.sorted { key($0) < key($1) }
    }
}
