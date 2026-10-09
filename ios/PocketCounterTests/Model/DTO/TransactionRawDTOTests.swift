import Foundation
import Testing

@testable import PocketCounter

@Suite("TransactionRawDTO")
struct TransactionRawDTOTests {
    private func decode(_ json: String) throws -> TransactionRawResponseDTO {
        try WireFixtures.decode(TransactionRawResponseDTO.self, json)
    }

    @Test("it decodes a live answer")
    func liveAnswer() throws {
        let dto = try decode(WireFixtures.Captured.supermarket)

        #expect(dto.reading.type == "EXPENSE")
        #expect(dto.reading.amount == Decimal(string: "150.00"))
        #expect(dto.reading.date == "2026-10-07")
        #expect(dto.reading.name == "Supermercado")
        #expect(dto.reading.paymentMethod == nil)
        #expect(dto.source.amount == "WRITTEN")
        #expect(dto.source.date == "INFERRED")
        #expect(dto.card.status == "NOT_APPLICABLE")
        #expect(dto.card.resolved == nil)
        #expect(dto.card.candidates.isEmpty)
        #expect(dto.tag.idTag == nil)
        #expect(dto.missing.isEmpty)
    }

    @Test("a live answer for a card sentence carries source.card and source.paymentMethod")
    func liveCard() throws {
        let dto = try decode(WireFixtures.Captured.gasolineOnNubank)

        #expect(dto.source.card == "WRITTEN")
        #expect(dto.source.paymentMethod == "INFERRED")
        #expect(dto.card.resolved?.name == "NuBank")
        #expect(dto.card.candidates.isEmpty)
    }

    @Test("source keys the server leaves out decode as nil, and a value it does not know decodes as text")
    func absentSourceKeys() throws {
        let dto = try decode(WireFixtures.rawResponse(
            reading: #"{"type":null,"amount":null,"date":"2026-10-07","name":null,"paymentMethod":"VOUCHER"}"#,
            source: #"{"date":"INFERRED"}"#,
            missing: #"["AMOUNT","DESCRIPTION","TYPE","SOMETHING_NEW"]"#
        ))

        #expect(dto.source.type == nil)
        #expect(dto.source.card == nil)
        #expect(dto.reading.paymentMethod == "VOUCHER")
        #expect(dto.missing == ["AMOUNT", "DESCRIPTION", "TYPE", "SOMETHING_NEW"])
    }

    @Test("candidates and the tag suggestion decode")
    func cardAndTag() throws {
        let dto = try decode(WireFixtures.rawResponse(
            card: #"{"status":"AMBIGUOUS","resolved":null,"candidates":[{"id":"k1","name":"Nubank Gold"},{"id":"k2","name":"Nubank Black"}]}"#,
            tag: #"{"idTag":"g1","idCategory":"c1"}"#
        ))

        #expect(dto.card.candidates.map(\.name) == ["Nubank Gold", "Nubank Black"])
        #expect(dto.tag.idTag == "g1")
    }

    @Test("the request carries the text and the reference date as yyyy-MM-dd")
    func request() throws {
        let body = TransactionRawRequestDTO(text: "gastei 150", referenceDate: "2026-10-07")

        let json = try JSONSerialization.jsonObject(with: JSONEncoder().encode(body)) as? [String: String]

        #expect(json == ["text": "gastei 150", "referenceDate": "2026-10-07"])
    }
}
