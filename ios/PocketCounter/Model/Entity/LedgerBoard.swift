import Foundation

struct LedgerBoard: Hashable, Sendable {
    enum Emptiness: Hashable, Sendable { case notEmpty, monthHasNoneOfKind, filteredOut }

    let groups: [LedgerGroup]
    let visibleCount: Int
    let total: Money
    let emptiness: Emptiness

    /// kind, then search, then só-fixos, then group.
    static func from(_ ledger: MonthLedger, filter: LedgerFilter, mode: LedgerGroupMode) -> LedgerBoard {
        let visible = filter.apply(to: ledger.items, lookups: ledger.lookups)
        return LedgerBoard(
            groups: LedgerGrouping.groups(of: visible, lookups: ledger.lookups, mode: mode, kind: filter.kind),
            visibleCount: visible.count,
            total: visible.map(\.amount.abs).sum(),
            emptiness: emptiness(visible: visible, of: ledger.items, kind: filter.kind)
        )
    }

    private static func emptiness(visible: [HistoryItem], of items: [HistoryItem], kind: TransactionType) -> Emptiness {
        guard visible.isEmpty else { return .notEmpty }
        return items.contains { $0.type == kind } ? .filteredOut : .monthHasNoneOfKind
    }
}
