import Foundation

struct MonthLedger: Hashable, Sendable {
    let ref: RefYearMonth
    let items: [HistoryItem]
    let lookups: LookupSet

    var kpis: HomeKpis { .from(items) }
    var openInvoices: OpenInvoices { .from(items, lookups: lookups) }

    func applying(_ statuses: [TransactionID: PaymentStatus]) -> MonthLedger {
        guard !statuses.isEmpty else { return self }
        let projected = items.map { item in
            statuses[item.id].map(item.settingPaymentStatus) ?? item
        }
        return MonthLedger(ref: ref, items: projected, lookups: lookups)
    }

    func removing(_ id: TransactionID) -> MonthLedger {
        MonthLedger(ref: ref, items: items.filter { $0.id != id }, lookups: lookups)
    }

    /// Stamps `displayOrder` with each named id's index; unnamed rows keep theirs. `items` holds both kinds.
    func reordering(_ order: [TransactionID]) -> MonthLedger {
        let positions = Dictionary(uniqueKeysWithValues: order.enumerated().map { ($1, $0) })
        let stamped = items.map { item in positions[item.id].map(item.settingDisplayOrder) ?? item }
        return MonthLedger(ref: ref, items: LedgerOrder.sorted(stamped), lookups: lookups)
    }
}
