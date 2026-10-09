import Foundation
import Testing

@testable import PocketCounter

@Suite("TransactionMapper")
struct TransactionMapperTests {
    private func map(_ json: String) throws -> HistoryItem {
        try TransactionMapper.map(WireFixtures.decode(TransactionDTO.self, json))
    }

    @Test("an expense takes a negative sign from its non-negative wire magnitude")
    func expenseSign() throws {
        let item = try map(WireFixtures.transaction(type: "EXPENSE", amount: "1234.56"))

        #expect(item.type == .expense)
        #expect(item.amount == Money(Decimal(string: "-1234.56") ?? 0))
    }

    @Test("a negative wire amount cannot flip the sign: an expense stays negative, an income positive")
    func signIgnoresWireSign() throws {
        let expense = try map(WireFixtures.transaction(type: "EXPENSE", amount: "-10.50"))
        let income = try map(WireFixtures.transaction(type: "INCOME", amount: "-10.50"))

        #expect(expense.amount == Money(Decimal(string: "-10.50") ?? 0))
        #expect(income.amount == Money(Decimal(string: "10.50") ?? 0))
    }

    @Test("an income keeps its sign")
    func incomeSign() throws {
        let item = try map(WireFixtures.transaction(type: "INCOME", amount: "99.9"))

        #expect(item.amount == Money(Decimal(string: "99.9") ?? 0))
    }

    @Test("a plain row maps every scalar field")
    func scalars() throws {
        let item = try map(WireFixtures.transaction(
            id: "t9", name: "Aluguel", status: "PENDING", displayOrder: 4, method: "PIX",
            cardId: "k1", isInvoice: true, idRecurringTransaction: "s1", dateDue: "2026-10-05"
        ))

        #expect(item.id == TransactionID(rawValue: "t9"))
        #expect(item.name == "Aluguel")
        #expect(item.statusPayment == .pending)
        #expect(item.displayOrder == 4)
        #expect(item.paymentMethod == .pix)
        #expect(item.cardId == CardID(rawValue: "k1"))
        #expect(item.isInvoice)
        #expect(item.recurringTransactionId == RecurringTransactionID(rawValue: "s1"))
        #expect(item.isFixo)
    }

    @Test("a due date in the next month keeps the wire ref month")
    func refOutlivesTheDueDate() throws {
        let item = try map(WireFixtures.transaction(ref: 202610, dateDue: "2026-11-10"))

        #expect(item.ref == RefYearMonth(raw: 202610))
        #expect(item.date == .of(2026, 11, 10))
    }

    @Test("an unknown transactionType throws instead of inventing an expense")
    func unknownType() {
        #expect(throws: MappingFailure.unknownEnum(entity: "Transaction", field: "transactionType", value: "TRANSFER")) {
            try map(WireFixtures.transaction(type: "TRANSFER"))
        }
    }

    @Test("an unknown statusPayment throws")
    func unknownStatus() {
        #expect(throws: MappingFailure.unknownEnum(entity: "Transaction", field: "statusPayment", value: "LATE")) {
            try map(WireFixtures.transaction(status: "LATE"))
        }
    }

    @Test("an unknown paymentMethod degrades to nil")
    func unknownMethod() throws {
        #expect(try map(WireFixtures.transaction(method: "BOLETO")).paymentMethod == nil)
    }

    @Test("tags null and absent stay nil, empty stays empty, and ids are carried")
    func tags() throws {
        #expect(try map(WireFixtures.transaction(tags: .absent)).tagIds == nil)
        #expect(try map(WireFixtures.transaction(tags: .value(nil))).tagIds == nil)
        #expect(try map(WireFixtures.transaction(tags: .value([]))).tagIds == [])
        let tagged = try map(WireFixtures.transaction(tags: .value([WireFixtures.tag(id: "g1"), WireFixtures.tag(id: "g2")])))
        #expect(tagged.tagIds == [TagID(rawValue: "g1"), TagID(rawValue: "g2")])
    }

    @Test("the date is dateDue, else datePaid, else day 1 of the row's month")
    func dates() throws {
        let both = try map(WireFixtures.transaction(dateDue: "2026-10-05", datePaid: "2026-10-20"))
        let paidOnly = try map(WireFixtures.transaction(datePaid: "2026-10-20"))
        let neither = try map(WireFixtures.transaction(ref: 202602))

        #expect(both.date == .of(2026, 10, 5))
        #expect(paidOnly.date == .of(2026, 10, 20))
        #expect(neither.date == .of(2026, 2, 1))
    }

    @Test("a malformed dateDue throws invalidDate")
    func malformedDate() {
        #expect(throws: MappingFailure.invalidDate(entity: "Transaction", value: "05/10/2026")) {
            try map(WireFixtures.transaction(dateDue: "05/10/2026"))
        }
    }

    @Test("a refYearMonth that is not a month throws invalidRef", arguments: [202613, 202600, 0])
    func invalidRef(ref: Int) {
        #expect(throws: MappingFailure.invalidRef(ref)) {
            try map(WireFixtures.transaction(ref: ref))
        }
    }

    @Test("an empty id is a missing field")
    func emptyId() {
        #expect(throws: MappingFailure.missingField(entity: "Transaction", field: "id")) {
            try map(WireFixtures.transaction(id: ""))
        }
    }

    @Test("an empty recurring transaction id is a missing field, not a fixo with an unusable path")
    func emptyRecurringTransactionId() {
        #expect(throws: MappingFailure.missingField(entity: "Transaction", field: "idRecurringTransaction")) {
            try map(WireFixtures.transaction(idRecurringTransaction: ""))
        }
    }
}
