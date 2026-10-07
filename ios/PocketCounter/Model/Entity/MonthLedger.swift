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
}
