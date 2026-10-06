import Foundation

enum InvoiceItemMapper {
    static func map(_ dto: TransactionItemDTO) throws(MappingFailure) -> InvoiceItem {
        guard !dto.id.isEmpty else { throw .missingField(entity: "InvoiceItem", field: "id") }
        guard !dto.idTransaction.isEmpty else { throw .missingField(entity: "InvoiceItem", field: "idTransaction") }
        return InvoiceItem(
            id: InvoiceItemID(rawValue: dto.id),
            invoiceId: TransactionID(rawValue: dto.idTransaction),
            name: dto.name,
            // Negative from the magnitude alone: an item inherits its invoice's sign, never the wire's.
            amount: Money(-Swift.abs(dto.amount)),
            purchasedOn: try purchaseDay(dto.datePurchase),
            originName: dto.originName,
            tagIds: dto.tags?.map { TagID(rawValue: $0.id) } ?? []
        )
    }

    private static func purchaseDay(_ text: String?) throws(MappingFailure) -> CalendarDay? {
        guard let text else { return nil }
        guard let day = try? CalendarDay(iso: text) else { throw .invalidDate(entity: "InvoiceItem", value: text) }
        return day
    }
}
