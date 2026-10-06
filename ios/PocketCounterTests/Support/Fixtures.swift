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
        seriesId: String? = nil,
        name: String? = nil,
        description: String? = nil
    ) -> HistoryItem {
        HistoryItem(
            id: TransactionID(rawValue: id),
            ref: ref ?? date.refYearMonth,
            date: date,
            amount: Money(amount),
            type: type,
            tagIds: tagIds,
            statusPayment: statusPayment,
            seriesId: seriesId,
            name: name,
            description: description
        )
    }

    static func expense(_ amount: Decimal, status: PaymentStatus = .paid) -> HistoryItem {
        .fixture(amount: -Swift.abs(amount), type: .expense, statusPayment: status)
    }

    static func income(_ amount: Decimal, status: PaymentStatus = .paid) -> HistoryItem {
        .fixture(amount: amount, type: .income, statusPayment: status)
    }
}

extension AuthenticatedUser {
    static let fixture = AuthenticatedUser(id: JWTFixture.userId, name: "Ana", email: "ana@b.com")
}
