import Foundation
import Testing

@testable import PocketCounter

@Suite("TransactionDTO")
struct TransactionDTOTests {
    private func decode(_ json: String) throws -> TransactionDTO {
        try JSONDecoder().decode(TransactionDTO.self, from: Data(json.utf8))
    }

    private func body(amount: String = "10.50", tags: String? = nil, extra: String = "") -> String {
        let tagsField = tags.map { #","tags":\#($0)"# } ?? ""
        return """
        {"id":"t1","transactionType":"EXPENSE","name":null,"description":null,"amount":\(amount),
        "statusPayment":"PENDING","refYearMonth":202610,"displayOrder":3,"paymentMethod":null,
        "cardId":null,"isInvoice":false,"idRecurringTransaction":null,"dateDue":"2026-10-05","datePaid":null\(tagsField)\(extra)}
        """
    }

    @Test("it decodes the backend's shape, with refYearMonth as an Int")
    func shape() throws {
        let dto = try decode(body())

        #expect(dto.id == "t1")
        #expect(dto.transactionType == "EXPENSE")
        #expect(dto.statusPayment == "PENDING")
        #expect(dto.refYearMonth == 202610)
        #expect(dto.displayOrder == 3)
        #expect(dto.dateDue == "2026-10-05")
        #expect(dto.datePaid == nil)
        #expect(!dto.isInvoice)
    }

    @Test("the amount keeps its exact decimal value", arguments: [
        ("1234.5678", Decimal(string: "1234.5678")),
        ("0.1", Decimal(string: "0.1")),
    ])
    func exactAmount(text: String, expected: Decimal?) throws {
        #expect(try decode(body(amount: text)).amount == expected)
    }

    @Test("tags null, absent, empty and populated are told apart where it matters")
    func tagStates() throws {
        let populated = #"[{"id":"g1","name":"Mercado","kind":"EXPENSE"}]"#

        #expect(try decode(body(tags: "null")).tags == nil)
        #expect(try decode(body()).tags == nil)
        #expect(try decode(body(tags: "[]")).tags?.isEmpty == true)
        #expect(try decode(body(tags: populated)).tags?.map(\.id) == ["g1"])
    }

    @Test("a recurring link arrives under the backend's key: absence would silently un-fixo the row")
    func recurringLink() throws {
        let json = """
        {"id":"t1","transactionType":"EXPENSE","name":"Aluguel","amount":1200,"statusPayment":"PENDING",
        "refYearMonth":202610,"displayOrder":0,"isInvoice":false,"idRecurringTransaction":"r1",
        "tags":[{"id":"g1","name":"Casa","kind":"EXPENSE","idRecurringTransaction":"r1"}]}
        """
        let dto = try decode(json)

        #expect(dto.idRecurringTransaction == "r1")
        #expect(dto.tags?.first?.idRecurringTransaction == "r1")
        #expect(try TransactionMapper.map(dto).isFixo)
    }

    @Test("an unknown extra field is ignored")
    func extraField() throws {
        #expect(try decode(body(extra: #","somethingNew":{"a":1}"#)).id == "t1")
    }

    @Test("a row missing a non-nullable field fails to decode")
    func strict() {
        #expect(throws: DecodingError.self) {
            try decode(#"{"id":"t1","transactionType":"EXPENSE","amount":1}"#)
        }
    }
}
