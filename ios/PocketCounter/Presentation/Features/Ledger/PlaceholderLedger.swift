import Foundation

/// Not `#if DEBUG`: redacted skeletons ship in Release. Amounts are shaped like real ones so the
/// redaction bars have realistic widths.
extension MonthLedger {
    static func placeholder(for ref: RefYearMonth) -> MonthLedger {
        MonthLedger(ref: ref, items: placeholderItems(in: ref), lookups: placeholderLookups)
    }

    private static func placeholderItems(in ref: RefYearMonth) -> [HistoryItem] {
        let rows: [(String, Int, TransactionType, PaymentStatus)] = [
            ("Salário", 720_000, .income, .paid),
            ("Aluguel", -185_000, .expense, .pending),
            ("Mercado", -18_490, .expense, .paid),
            ("Restaurante", -6_450, .expense, .paid),
        ]
        let day = CalendarDay.first(of: ref)
        return rows.enumerated().map { index, row in
            HistoryItem(
                id: TransactionID(rawValue: "placeholder-\(index)"),
                ref: ref,
                date: day,
                amount: Money(Decimal(row.1) / 100),
                type: row.2,
                tagIds: nil,
                statusPayment: row.3,
                displayOrder: index,
                name: row.0
            )
        }
    }

    private static let placeholderLookups = LookupSet(
        categories: [TagContext(id: ContextID(rawValue: "placeholder-context"), name: "Casa", color: nil)],
        tags: [Tag(id: TagID(rawValue: "placeholder-tag"), name: "Mercado", kind: .expense)],
        cards: [CreditCard(id: CardID(rawValue: "placeholder-card"), name: "Cartão", brand: nil, closingDay: nil, color: nil)]
    )
}
