import Foundation

struct MonthLedger: Hashable, Sendable {
    let ref: RefYearMonth
    let items: [HistoryItem]
    let lookups: LookupSet

    var kpis: HomeKpis { .from(items) }
}
