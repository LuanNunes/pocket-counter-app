import Foundation
import Testing

@testable import PocketCounter

@Suite("Catalog DTOs")
struct CatalogDTOTests {
    private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
        try JSONDecoder().decode(type, from: Data(json.utf8))
    }

    @Test("a full tag decodes")
    func fullTag() throws {
        let tag = try decode(TagDTO.self, ##"{"id":"g1","idUser":"u","name":"Mercado","kind":"INCOME","idCategory":"c1","color":"#112233","idRecurringTransaction":"s1"}"##)

        #expect(tag.kind == "INCOME")
        #expect(tag.idCategory == "c1")
        #expect(tag.color == "#112233")
        #expect(tag.idRecurringTransaction == "s1")
    }

    @Test("the five-argument embedded tag has no colour and decodes without error")
    func embeddedTag() throws {
        let tag = try decode(
            TagDTO.self,
            #"{"id":"g1","idUser":"u","idCategory":"c1","idTransaction":"t1","name":"Mercado","kind":"EXPENSE","color":null,"idRecurringTransaction":null}"#
        )

        #expect(tag.color == nil)
        #expect(tag.idRecurringTransaction == nil)
    }

    @Test("a category decodes with optional colour and order")
    func category() throws {
        let full = try decode(CategoryDTO.self, ##"{"id":"c1","name":"Casa","color":"#FF0000","displayOrder":2}"##)
        let bare = try decode(CategoryDTO.self, #"{"id":"c2","name":"Lazer"}"#)

        #expect(full.displayOrder == 2)
        #expect(bare.color == nil)
        #expect(bare.displayOrder == nil)
    }

    @Test("a credit card decodes and ignores fields it does not model")
    func card() throws {
        let card = try decode(CreditCardDTO.self, #"{"id":"k1","idUser":"u","name":"Nubank","brand":"mastercard","closingDay":10,"color":null,"limit":5000}"#)

        #expect(card.brand == "mastercard")
        #expect(card.closingDay == 10)
        #expect(card.color == nil)
    }

    @Test("an invoice item decodes its exact amount and tag states")
    func item() throws {
        let json = #"{"id":"i1","idTransaction":"t1","name":"Uber","amount":1234.5678,"datePurchase":"2026-10-02","originName":"UBER *TRIP","tags":[]}"#
        let item = try decode(TransactionItemDTO.self, json)
        let bare = try decode(TransactionItemDTO.self, #"{"id":"i2","idTransaction":"t1","name":"X","amount":0.1}"#)

        #expect(item.amount == Decimal(string: "1234.5678"))
        #expect(item.tags?.isEmpty == true)
        #expect(item.originName == "UBER *TRIP")
        #expect(bare.amount == Decimal(string: "0.1"))
        #expect(bare.tags == nil)
        #expect(bare.datePurchase == nil)
    }
}
