import Foundation

enum TransactionMapper {
    static func map(_ dto: TransactionDTO) throws(MappingFailure) -> HistoryItem {
        guard !dto.id.isEmpty else { throw .missingField(entity: "Transaction", field: "id") }
        guard let type = TransactionType(wire: dto.transactionType) else {
            throw .unknownEnum(entity: "Transaction", field: "transactionType", value: dto.transactionType)
        }
        guard let status = PaymentStatus(wire: dto.statusPayment) else {
            throw .unknownEnum(entity: "Transaction", field: "statusPayment", value: dto.statusPayment)
        }
        guard let ref = RefYearMonth(raw: dto.refYearMonth) else { throw .invalidRef(dto.refYearMonth) }
        if let cardId = dto.cardId, cardId.isEmpty { throw .missingField(entity: "Transaction", field: "cardId") }
        // The sign comes from the type alone, never from the wire amount.
        let magnitude = Swift.abs(dto.amount)
        return HistoryItem(
            id: TransactionID(rawValue: dto.id),
            ref: ref,
            date: try day(dto, in: ref),
            amount: type == .expense ? Money(-magnitude) : Money(magnitude),
            type: type,
            // The outer map keeps nil (no override) apart from [] (an override with none).
            tagIds: dto.tags.map { $0.map { TagID(rawValue: $0.id) } },
            statusPayment: status,
            displayOrder: dto.displayOrder,
            paymentMethod: dto.paymentMethod.flatMap { PaymentMethod(wire: $0) },
            cardId: dto.cardId.map { CardID(rawValue: $0) },
            seriesId: dto.idSeries,
            name: dto.name,
            description: dto.description,
            isInvoice: dto.isInvoice
        )
    }

    // Not datePaid first: paying a row sets it to about today, which would move the row to another day-group.
    private static func day(_ dto: TransactionDTO, in ref: RefYearMonth) throws(MappingFailure) -> CalendarDay {
        if let due = dto.dateDue { return try parse(due) }
        if let paid = dto.datePaid { return try parse(paid) }
        guard let first = try? CalendarDay(year: ref.year, month: ref.month, day: 1) else { throw .invalidRef(ref.raw) }
        return first
    }

    private static func parse(_ text: String) throws(MappingFailure) -> CalendarDay {
        guard let day = try? CalendarDay(iso: text) else { throw .invalidDate(entity: "Transaction", value: text) }
        return day
    }
}
