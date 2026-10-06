import Foundation

/// Not `#if DEBUG`: redacted skeletons ship in Release. Amounts are shaped like real ones so the
/// redaction bars have realistic widths.
extension MonthLedger {
    static func placeholder(for ref: RefYearMonth) -> MonthLedger {
        MonthLedger(ref: ref, items: placeholderItems(in: ref) + [placeholderInvoice(in: ref)], lookups: placeholderLookups)
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

    private static func placeholderInvoice(in ref: RefYearMonth) -> HistoryItem {
        HistoryItem(
            id: TransactionID(rawValue: "placeholder-invoice"),
            ref: ref,
            date: .first(of: ref),
            amount: Money(Decimal(-324_090) / 100),
            type: .expense,
            tagIds: nil,
            statusPayment: .pending,
            displayOrder: 4,
            cardId: placeholderCard.id,
            name: "Fatura",
            isInvoice: true
        )
    }

    private static let placeholderCard = CreditCard(
        id: CardID(rawValue: "placeholder-card"), name: "Cartão", brand: nil, closingDay: nil, color: nil
    )

    private static let placeholderLookups = LookupSet(
        categories: [TagContext(id: ContextID(rawValue: "placeholder-context"), name: "Casa", color: nil)],
        tags: [Tag(id: TagID(rawValue: "placeholder-tag"), name: "Mercado", kind: .expense)],
        cards: [placeholderCard]
    )
}
