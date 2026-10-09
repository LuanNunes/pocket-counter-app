import Foundation
import Testing

@testable import PocketCounter

@Suite("SentenceReadingMapper")
struct SentenceReadingMapperTests {
    private func map(_ json: String) throws -> SentenceReading {
        try SentenceReadingMapper.map(WireFixtures.decode(TransactionRawResponseDTO.self, json))
    }

    private func reading(
        type: String = "null", amount: String = "250.00", name: String = #""Consulta""#, method: String = "null"
    ) -> String {
        #"{"type":\#(type),"amount":\#(amount),"date":"2026-10-07","name":\#(name),"paymentMethod":\#(method)}"#
    }

    @Test("a sentence that names everything maps with each field's source beside it")
    func supermarket() throws {
        let result = try map(WireFixtures.Captured.supermarket)

        #expect(result == SentenceReading(
            type: Sourced(value: .expense, source: .written),
            amount: Sourced(value: Money(150), source: .written),
            date: Sourced(value: .of(2026, 10, 7), source: .inferred),
            name: Sourced(value: "Supermercado", source: .written),
            paymentMethod: nil,
            card: .notApplicable,
            tag: nil,
            missing: []
        ))
    }

    @Test("a date the sentence wrote is resolved by the server and badged written; type stays nil")
    func yesterday() throws {
        let result = try map(WireFixtures.Captured.yesterday)

        #expect(result.date == Sourced(value: .of(2026, 10, 6), source: .written))
        #expect(result.amount == Sourced(value: Money(68), source: .written))
        #expect(result.type == nil)
        #expect(result.name == nil)
        #expect(result.missing == [.description, .type])
    }

    @Test("an amount the sentence lacks is nil and asked for")
    func noAmount() throws {
        let result = try map(WireFixtures.Captured.noAmount)

        #expect(result.amount == nil)
        #expect(result.name == Sourced(value: "Mercado", source: .written))
        #expect(result.missing == [.amount])
    }

    @Test("an unnamed row with two cards keeps both and the server's queue")
    func ambiguousCredit() throws {
        let result = try map(WireFixtures.Captured.ambiguousCredit)

        #expect(result.name == nil)
        #expect(result.paymentMethod == Sourced(value: .credit, source: .written))
        #expect(result.card == .ambiguous([
            .fixture("d0000000-0000-0000-0000-000000000001", "NuBank"),
            .fixture("d0000000-0000-0000-0000-000000000002", "Itaú"),
        ]))
        #expect(result.missing == [.description, .card])
    }

    @Test("a missing field with no value is absent from both sides and maps to nil")
    func absentFields() throws {
        let result = try map(WireFixtures.rawResponse(
            reading: reading(type: "null", amount: "null", name: "null"),
            source: #"{"date":"INFERRED"}"#,
            missing: #"["AMOUNT","DESCRIPTION","TYPE"]"#
        ))

        #expect(result.type == nil)
        #expect(result.amount == nil)
        #expect(result.name == nil)
        #expect(result.missing == [.amount, .description, .type])
    }

    @Test("a source with no value is dropped")
    func sourceWithoutValue() throws {
        let result = try map(WireFixtures.rawResponse(
            reading: reading(amount: "null"), source: #"{"amount":"WRITTEN","name":"WRITTEN","date":"INFERRED"}"#
        ))

        #expect(result.amount == nil)
    }

    @Test("a value with no source is a failure: an unbadged field is forbidden", arguments: [
        (#"{"type":"EXPENSE","amount":250,"date":"2026-10-07","name":"x","paymentMethod":null}"#, #"{"amount":"WRITTEN","name":"WRITTEN","date":"INFERRED"}"#, "type"),
        (#"{"type":"EXPENSE","amount":250,"date":"2026-10-07","name":"x","paymentMethod":null}"#, #"{"type":"WRITTEN","name":"WRITTEN","date":"INFERRED"}"#, "amount"),
        (#"{"type":"EXPENSE","amount":250,"date":"2026-10-07","name":"x","paymentMethod":null}"#, #"{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED"}"#, "name"),
        (#"{"type":"EXPENSE","amount":250,"date":"2026-10-07","name":"x","paymentMethod":"PIX"}"#, #"{"type":"WRITTEN","amount":"WRITTEN","name":"WRITTEN","date":"INFERRED"}"#, "paymentMethod"),
    ])
    func valueWithoutSource(reading: String, source: String, field: String) {
        #expect(throws: MappingFailure.missingField(entity: "SentenceReading", field: "source.\(field)")) {
            try map(WireFixtures.rawResponse(reading: reading, source: source))
        }
    }

    @Test("an unknown source, card status or type throws: each decides what the user is told")
    func unknownEnums() {
        #expect(throws: MappingFailure.unknownEnum(entity: "SentenceReading", field: "source.amount", value: "GUESSED")) {
            try map(WireFixtures.rawResponse(source: #"{"type":"WRITTEN","amount":"GUESSED","date":"INFERRED","name":"WRITTEN"}"#))
        }
        #expect(throws: MappingFailure.unknownEnum(entity: "SentenceReading", field: "source.date", value: "GUESSED")) {
            try map(WireFixtures.rawResponse(source: #"{"type":"WRITTEN","amount":"WRITTEN","date":"GUESSED","name":"WRITTEN"}"#))
        }
        #expect(throws: MappingFailure.unknownEnum(entity: "SentenceReading", field: "card.status", value: "MAYBE")) {
            try map(WireFixtures.rawResponse(card: #"{"status":"MAYBE","resolved":null,"candidates":[]}"#))
        }
        #expect(throws: MappingFailure.unknownEnum(entity: "SentenceReading", field: "reading.type", value: "TRANSFER")) {
            try map(WireFixtures.rawResponse(reading: reading(type: #""TRANSFER""#)))
        }
    }

    @Test("a question the client cannot ask must not vanish: an unknown missing field throws")
    func unknownMissingField() {
        #expect(throws: MappingFailure.unknownEnum(entity: "SentenceReading", field: "missing", value: "TAG")) {
            try map(WireFixtures.rawResponse(missing: #"["AMOUNT","TAG"]"#))
        }
    }

    @Test("an unknown payment method degrades to nil: it only decorates")
    func unknownPaymentMethod() throws {
        let result = try map(WireFixtures.rawResponse(
            reading: reading(method: #""VOUCHER""#),
            source: #"{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","name":"WRITTEN","paymentMethod":"WRITTEN"}"#
        ))

        #expect(result.paymentMethod == nil)
    }

    @Test("a payment method keeps its own source, apart from the card's")
    func methodAndCardSourcesDiffer() throws {
        let result = try map(WireFixtures.Captured.gasolineOnNubank)

        #expect(result.paymentMethod == Sourced(value: .credit, source: .inferred))
        #expect(result.card == .resolved(Sourced(
            value: CardCandidate.fixture("d0000000-0000-0000-0000-000000000001", "NuBank"), source: .written
        )))
    }

    @Test("an unparseable date is an invalid date")
    func badDate() {
        let bad = #"{"type":null,"amount":null,"date":"07/10/2026","name":null,"paymentMethod":null}"#

        #expect(throws: MappingFailure.invalidDate(entity: "SentenceReading", value: "07/10/2026")) {
            try map(WireFixtures.rawResponse(reading: bad, source: #"{"date":"INFERRED"}"#))
        }
    }

    @Test("an unresolved card maps to unresolved")
    func unresolvedCard() throws {
        let result = try map(WireFixtures.rawResponse(card: #"{"status":"UNRESOLVED","resolved":null,"candidates":[]}"#))

        #expect(result.card == .unresolved)
    }

    /// The DTO keeps `date` optional so an absent one names itself here, rather than failing the
    /// whole decode with nothing to show for it.
    @Test("an absent date is a mapping failure that names the field")
    func absentDate() {
        #expect(throws: MappingFailure.missingField(entity: "SentenceReading", field: "date")) {
            try map(WireFixtures.rawResponse(
                reading: #"{"type":"EXPENSE","amount":150.00,"name":"Supermercado","paymentMethod":null}"#
            ))
        }
    }

    @Test("an ambiguous card with no candidates is a failure")
    func ambiguousWithoutCandidates() {
        #expect(throws: MappingFailure.missingField(entity: "SentenceReading", field: "card.candidates")) {
            try map(WireFixtures.rawResponse(card: #"{"status":"AMBIGUOUS","resolved":null,"candidates":[]}"#))
        }
    }

    @Test("a tag suggestion with an empty id is dropped")
    func emptyTagId() throws {
        let result = try map(WireFixtures.rawResponse(tag: #"{"idTag":"","idCategory":null}"#))

        #expect(result.tag == nil)
    }

    @Test("a resolved card with no card or no source is a failure")
    func resolvedCardIncomplete() {
        #expect(throws: MappingFailure.missingField(entity: "SentenceReading", field: "card.resolved")) {
            try map(WireFixtures.rawResponse(card: #"{"status":"RESOLVED","resolved":null,"candidates":[]}"#))
        }
        #expect(throws: MappingFailure.missingField(entity: "SentenceReading", field: "source.card")) {
            try map(WireFixtures.rawResponse(card: #"{"status":"RESOLVED","resolved":{"id":"k1","name":"Nubank"},"candidates":[]}"#))
        }
    }

    @Test("NOT_APPLICABLE with CREDIT is a credit row with no card, not a sentence that named none")
    func notApplicableCredit() throws {
        let result = try map(WireFixtures.rawResponse(
            reading: reading(method: #""CREDIT""#),
            source: #"{"type":"WRITTEN","amount":"WRITTEN","date":"INFERRED","name":"WRITTEN","paymentMethod":"WRITTEN"}"#,
            card: #"{"status":"NOT_APPLICABLE","resolved":null,"candidates":[]}"#
        ))

        #expect(result.paymentMethod == Sourced(value: .credit, source: .written))
        #expect(result.card == .notApplicable)
        #expect(result.missing.isEmpty)
    }

    @Test("a candidate with no id is a failure")
    func candidateWithoutId() {
        #expect(throws: MappingFailure.missingField(entity: "SentenceReading", field: "card.candidates.id")) {
            try map(WireFixtures.rawResponse(card: #"{"status":"AMBIGUOUS","resolved":null,"candidates":[{"id":"","name":"x"}]}"#))
        }
    }

    @Test("the tag suggestion maps to its id")
    func tag() throws {
        let result = try map(WireFixtures.rawResponse(tag: #"{"idTag":"g1","idCategory":"c1"}"#))

        #expect(result.tag == TagID(rawValue: "g1"))
    }

    @Test("the missing queue keeps the server's order")
    func missingOrder() throws {
        let result = try map(WireFixtures.rawResponse(missing: #"["TYPE","CARD","DESCRIPTION","AMOUNT"]"#))

        #expect(result.missing == [.type, .card, .description, .amount])
    }

    @Test("the amount is never negated: it is a magnitude")
    func magnitude() throws {
        let result = try map(WireFixtures.rawResponse(reading: reading(type: #""EXPENSE""#, amount: "320.50")))

        #expect(result.amount?.value == Money(Decimal(string: "320.50") ?? 0))
    }
}
