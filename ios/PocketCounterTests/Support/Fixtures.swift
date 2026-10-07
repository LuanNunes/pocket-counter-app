import Foundation

@testable import PocketCounter

extension CalendarDay {
    static let fixture = CalendarDay.of(2026, 10, 3)

    static func of(_ year: Int, _ month: Int, _ day: Int) -> CalendarDay {
        do {
            return try CalendarDay(year: year, month: month, day: day)
        } catch {
            fatalError("invalid fixture day: \(error)")
        }
    }
}

extension HistoryItem {
    static func fixture(
        id: String = "t1",
        ref: RefYearMonth? = nil,
        date: CalendarDay = .fixture,
        amount: Decimal = 10,
        type: TransactionType = .expense,
        tagIds: [TagID]? = nil,
        statusPayment: PaymentStatus = .paid,
        displayOrder: Int = 0,
        seriesId: String? = nil,
        name: String? = nil,
        description: String? = nil,
        isInvoice: Bool = false,
        cardId: CardID? = nil
    ) -> HistoryItem {
        HistoryItem(
            id: TransactionID(rawValue: id),
            ref: ref ?? date.refYearMonth,
            date: date,
            amount: Money(amount),
            type: type,
            tagIds: tagIds,
            statusPayment: statusPayment,
            displayOrder: displayOrder,
            cardId: cardId,
            seriesId: seriesId.map { SeriesID(rawValue: $0) },
            name: name,
            description: description,
            isInvoice: isInvoice
        )
    }

    static func expense(_ amount: Decimal, status: PaymentStatus = .paid) -> HistoryItem {
        .fixture(amount: -Swift.abs(amount), type: .expense, statusPayment: status)
    }

    static func invoice(_ amount: Decimal, status: PaymentStatus = .pending, card: CardID? = nil) -> HistoryItem {
        .fixture(amount: -Swift.abs(amount), type: .expense, statusPayment: status, isInvoice: true, cardId: card)
    }

    static func income(_ amount: Decimal, status: PaymentStatus = .paid) -> HistoryItem {
        .fixture(amount: amount, type: .income, statusPayment: status)
    }
}

extension AuthenticatedUser {
    static let fixture = AuthenticatedUser(id: JWTFixture.userId, name: "Ana", email: "ana@b.com")
}

extension TagID {
    static func of(_ raw: String) -> TagID { TagID(rawValue: raw) }
}

extension ContextID {
    static func of(_ raw: String) -> ContextID { ContextID(rawValue: raw) }
}

extension Tag {
    static func fixture(
        _ id: String, _ name: String = "", kind: TransactionType = .expense, context: String? = nil
    ) -> Tag {
        Tag(id: .of(id), name: name.isEmpty ? id : name, kind: kind, contextId: context.map(ContextID.of))
    }
}

extension TagContext {
    static func fixture(_ id: String, _ name: String = "") -> TagContext {
        TagContext(id: .of(id), name: name.isEmpty ? id : name, color: nil)
    }
}

extension LookupSet {
    static func fixture(
        categories: [TagContext] = [], tags: [Tag] = [], failed: Set<LookupKind> = []
    ) -> LookupSet {
        LookupSet(categories: categories, tags: tags, cards: [], failed: failed)
    }
}
