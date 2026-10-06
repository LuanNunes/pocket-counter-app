import Foundation
import Testing

@testable import PocketCounter

@Suite("TagMapper")
struct TagMapperTests {

    @Test("a tag maps its kind, context, and colour")
    func tag() throws {
        let dto = try WireFixtures.decode(TagDTO.self, WireFixtures.tag(kind: "INCOME", color: "#112233"))

        #expect(try TagMapper.tag(dto) == Tag(
            id: TagID(rawValue: "g1"), name: "Mercado", kind: .income,
            contextId: ContextID(rawValue: "c1"), color: 0xFF112233
        ))
    }

    @Test("a garbage or missing colour is nil, not a failure", arguments: [nil, "azul"] as [String?])
    func colourDegrades(color: String?) throws {
        let dto = try WireFixtures.decode(TagDTO.self, WireFixtures.tag(color: color))

        #expect(try TagMapper.tag(dto).color == nil)
    }

    @Test("an unknown tag kind throws")
    func unknownKind() throws {
        let dto = try WireFixtures.decode(TagDTO.self, WireFixtures.tag(kind: "TRANSFER"))

        #expect(throws: MappingFailure.unknownEnum(entity: "Tag", field: "kind", value: "TRANSFER")) {
            try TagMapper.tag(dto)
        }
    }

    @Test("an empty tag id is a missing field")
    func emptyId() throws {
        let dto = try WireFixtures.decode(TagDTO.self, WireFixtures.tag(id: ""))

        #expect(throws: MappingFailure.missingField(entity: "Tag", field: "id")) { try TagMapper.tag(dto) }
    }

    @Test("a category maps to a context with an optional colour")
    func category() throws {
        let coloured = try WireFixtures.decode(CategoryDTO.self, WireFixtures.category(color: "#FF0000"))
        let plain = try WireFixtures.decode(CategoryDTO.self, WireFixtures.category(id: "c2", color: nil))

        #expect(try TagMapper.context(coloured) == TagContext(id: ContextID(rawValue: "c1"), name: "Casa", color: 0xFFFF0000))
        #expect(try TagMapper.context(plain).color == nil)
    }

    @Test("an empty category id is a missing field")
    func emptyCategoryId() throws {
        let dto = try WireFixtures.decode(CategoryDTO.self, WireFixtures.category(id: ""))

        #expect(throws: MappingFailure.missingField(entity: "Category", field: "id")) { try TagMapper.context(dto) }
    }
}

@Suite("CreditCardMapper")
struct CreditCardMapperTests {

    @Test("a card maps what the wire has")
    func card() throws {
        let dto = try WireFixtures.decode(CreditCardDTO.self, WireFixtures.card(brand: "visa", closingDay: 10, color: "#00FF00"))

        #expect(try CreditCardMapper.map(dto) == CreditCard(
            id: CardID(rawValue: "k1"), name: "Nubank", brand: "visa", closingDay: 10, color: 0xFF00FF00
        ))
    }

    @Test("absent optionals stay nil")
    func bare() throws {
        let card = try CreditCardMapper.map(WireFixtures.decode(CreditCardDTO.self, WireFixtures.card()))

        #expect(card.brand == nil)
        #expect(card.closingDay == nil)
        #expect(card.color == nil)
    }

    @Test("an empty id is a missing field")
    func emptyId() throws {
        let dto = try WireFixtures.decode(CreditCardDTO.self, WireFixtures.card(id: ""))

        #expect(throws: MappingFailure.missingField(entity: "CreditCard", field: "id")) { try CreditCardMapper.map(dto) }
    }
}

@Suite("InvoiceItemMapper")
struct InvoiceItemMapperTests {
    private func map(_ json: String) throws -> InvoiceItem {
        try InvoiceItemMapper.map(WireFixtures.decode(TransactionItemDTO.self, json))
    }

    @Test("an item is negative like its invoice and carries its fields")
    func item() throws {
        let item = try map(WireFixtures.item(amount: "20.5", datePurchase: "2026-10-02", originName: "UBER *TRIP"))

        #expect(item == InvoiceItem(
            id: InvoiceItemID(rawValue: "i1"), invoiceId: TransactionID(rawValue: "t1"), name: "Uber",
            amount: Money(Decimal(string: "-20.5") ?? 0), purchasedOn: .of(2026, 10, 2),
            originName: "UBER *TRIP", tagIds: []
        ))
    }

    @Test("a negative wire amount still maps to a negative item")
    func negativeWireAmount() throws {
        #expect(try map(WireFixtures.item(amount: "-20.5")).amount == Money(Decimal(string: "-20.5") ?? 0))
    }

    @Test("tags null, absent and empty all mean no tags; ids are carried")
    func tags() throws {
        #expect(try map(WireFixtures.item(tags: .absent)).tagIds == [])
        #expect(try map(WireFixtures.item(tags: .value(nil))).tagIds == [])
        #expect(try map(WireFixtures.item(tags: .value([]))).tagIds == [])
        #expect(try map(WireFixtures.item(tags: .value([WireFixtures.tag(id: "g7")]))).tagIds == [TagID(rawValue: "g7")])
    }

    @Test("a missing purchase date is nil and a malformed one throws")
    func dates() throws {
        #expect(try map(WireFixtures.item()).purchasedOn == nil)
        #expect(throws: MappingFailure.invalidDate(entity: "InvoiceItem", value: "ontem")) {
            try map(WireFixtures.item(datePurchase: "ontem"))
        }
    }

    @Test("an empty id or invoice id is a missing field")
    func emptyIds() {
        #expect(throws: MappingFailure.missingField(entity: "InvoiceItem", field: "id")) { try map(WireFixtures.item(id: "")) }
        #expect(throws: MappingFailure.missingField(entity: "InvoiceItem", field: "idTransaction")) {
            try map(WireFixtures.item(idTransaction: ""))
        }
    }
}
