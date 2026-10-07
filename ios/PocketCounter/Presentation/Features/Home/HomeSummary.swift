import Foundation

/// Every figure Início shows, derived once. `kpis` is computed from `items`, so the screen
/// reads it from here rather than re-deriving it per tile.
struct HomeSummary: Hashable, Sendable {
    let kpis: HomeKpis
    let openInvoices: OpenInvoices
    let transactionCount: Int

    var showsPending: Bool { kpis.pendingCount > 0 }

    static func from(_ ledger: MonthLedger) -> HomeSummary {
        HomeSummary(
            kpis: ledger.kpis,
            openInvoices: ledger.openInvoices,
            transactionCount: ledger.items.count
        )
    }
}
