import Foundation

struct RangeLedger: Hashable, Sendable {
    let span: RefYearMonthRange
    let items: [HistoryItem]
    let lookups: LookupSet

    /// One entry per month of the span, so an empty month is zero instead of missing.
    func totalsByMonth() -> [(RefYearMonth, TransactionTotals)] {
        let byMonth = Dictionary(grouping: items, by: { $0.ref })
        return span.months.map { ($0, TransactionTotals.from(byMonth[$0] ?? [])) }
    }
}
