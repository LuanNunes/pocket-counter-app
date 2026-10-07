import Foundation
import Testing

@testable import PocketCounter

@Suite("RecurringSeriesMapper")
struct RecurringSeriesMapperTests {
    private func map(_ json: String) throws -> RecurringSeries {
        try RecurringSeriesMapper.map(WireFixtures.decode(RecurringSeriesDTO.self, json))
    }

    @Test("a series maps its id, name, type and day")
    func series() throws {
        #expect(try map(WireFixtures.series()) == RecurringSeries(
            id: SeriesID(rawValue: "s1"), name: "Aluguel", type: .expense, recurrenceDay: 5))
    }

    @Test("a missing recurrence day is nil")
    func noDay() throws {
        #expect(try map(WireFixtures.series(type: "INCOME", recurrenceDay: nil)).recurrenceDay == nil)
    }

    @Test("an unknown type throws: it decides which series a row joins")
    func unknownType() {
        #expect(throws: MappingFailure.unknownEnum(entity: "RecurringSeries", field: "transactionType", value: "TRANSFER")) {
            try map(WireFixtures.series(type: "TRANSFER"))
        }
    }

    @Test("a missing or empty id is a missing field", arguments: [nil, ""] as [String?])
    func noId(id: String?) {
        #expect(throws: MappingFailure.missingField(entity: "RecurringSeries", field: "id")) {
            try map(WireFixtures.series(id: id))
        }
    }

    @Test("a missing name is a missing field")
    func noName() {
        #expect(throws: MappingFailure.missingField(entity: "RecurringSeries", field: "name")) {
            try map(WireFixtures.series(name: nil))
        }
    }

    @Test("a missing type is a missing field")
    func noType() {
        #expect(throws: MappingFailure.missingField(entity: "RecurringSeries", field: "transactionType")) {
            try map(WireFixtures.series(type: nil))
        }
    }

    @Test("a body with keys absent decodes")
    func absentKeys() throws {
        #expect(try map(#"{"id":"s2","name":"Luz","transactionType":"EXPENSE"}"#).recurrenceDay == nil)
    }
}
