import Foundation
import Testing

@testable import PocketCounter

@Suite("RecurringTransactionMapper")
struct RecurringTransactionMapperTests {
    private func map(_ json: String) throws -> RecurringTransaction {
        try RecurringTransactionMapper.map(WireFixtures.decode(RecurringTransactionDTO.self, json))
    }

    @Test("a recurring transaction maps its id, name and type")
    func recurringTransaction() throws {
        #expect(try map(WireFixtures.recurringTransaction()) == RecurringTransaction(
            id: RecurringTransactionID(rawValue: "s1"), name: "Aluguel", type: .expense))
    }

    @Test("an unknown type throws: it decides which recurring transaction a row joins")
    func unknownType() {
        #expect(throws: MappingFailure.unknownEnum(entity: "RecurringTransaction", field: "transactionType", value: "TRANSFER")) {
            try map(WireFixtures.recurringTransaction(type: "TRANSFER"))
        }
    }

    @Test("a missing or empty id is a missing field", arguments: [nil, ""] as [String?])
    func noId(id: String?) {
        #expect(throws: MappingFailure.missingField(entity: "RecurringTransaction", field: "id")) {
            try map(WireFixtures.recurringTransaction(id: id))
        }
    }

    @Test("a missing name is a missing field")
    func noName() {
        #expect(throws: MappingFailure.missingField(entity: "RecurringTransaction", field: "name")) {
            try map(WireFixtures.recurringTransaction(name: nil))
        }
    }

    @Test("a missing type is a missing field")
    func noType() {
        #expect(throws: MappingFailure.missingField(entity: "RecurringTransaction", field: "transactionType")) {
            try map(WireFixtures.recurringTransaction(type: nil))
        }
    }
}
