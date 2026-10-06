import Foundation

/// Matches Android's `RetrofitCardRepository`: per card, its invoice row's amount whatever its
/// status, else the sum of its plain expenses. A failed cards lookup leaves both figures unknown.
struct OpenInvoices: Hashable, Sendable {
    /// A positive magnitude: invoice rows are expenses and carry a negative amount.
    let total: Money?
    /// `nil` means the cards lookup failed; `0` means there are no cards.
    let cardCount: Int?

    static func from(_ items: [HistoryItem], lookups: LookupSet) -> OpenInvoices {
        guard !lookups.failed.contains(.cards) else {
            return OpenInvoices(total: nil, cardCount: nil)
        }
        let total = lookups.cards.map { invoiceAmount(of: $0, in: items) }.sum()
        return OpenInvoices(total: total, cardCount: lookups.cards.count)
    }

    private static func invoiceAmount(of card: CreditCard, in items: [HistoryItem]) -> Money {
        let cardItems = items.filter { $0.cardId == card.id && $0.type == .expense }
        if let invoice = cardItems.first(where: \.isInvoice) {
            return invoice.amount.abs
        }
        return cardItems.map(\.amount.abs).sum()
    }

    private init(total: Money?, cardCount: Int?) {
        self.total = total
        self.cardCount = cardCount
    }
}
