import Foundation

enum CreditCardMapper {
    static func map(_ dto: CreditCardDTO) throws(MappingFailure) -> CreditCard {
        guard !dto.id.isEmpty else { throw .missingField(entity: "CreditCard", field: "id") }
        return CreditCard(
            id: CardID(rawValue: dto.id),
            name: dto.name,
            brand: dto.brand,
            closingDay: dto.closingDay,
            color: HexColor.argb(dto.color)
        )
    }
}
