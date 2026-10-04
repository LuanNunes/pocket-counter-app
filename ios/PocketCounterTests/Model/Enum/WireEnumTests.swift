import Testing

@testable import PocketCounter

@Suite("Wire enums")
struct WireEnumTests {

    @Test("transaction types parse the backend's uppercase", arguments: [
        ("INCOME", TransactionType.income), ("EXPENSE", .expense),
    ])
    func transactionType(wire: String, expected: TransactionType) {
        #expect(TransactionType(wire: wire) == expected)
    }

    @Test("payment statuses parse the backend's uppercase", arguments: [
        ("PAID", PaymentStatus.paid), ("PENDING", .pending),
    ])
    func paymentStatus(wire: String, expected: PaymentStatus) {
        #expect(PaymentStatus(wire: wire) == expected)
    }

    @Test("payment methods parse the backend's uppercase", arguments: [
        ("CREDIT", PaymentMethod.credit), ("DEBIT", .debit), ("PIX", .pix),
        ("CASH", .cash), ("CRYPTO", .crypto),
    ])
    func paymentMethod(wire: String, expected: PaymentMethod) {
        #expect(PaymentMethod(wire: wire) == expected)
    }

    @Test("an unknown wire value degrades to nil instead of crashing", arguments: ["", "BOLETO", "income"])
    func unknown(wire: String) {
        #expect(TransactionType(wire: wire) == nil)
        #expect(PaymentStatus(wire: wire) == nil)
        #expect(PaymentMethod(wire: wire) == nil)
    }

    @Test("wire values round-trip through the raw value")
    func wireRoundTrip() {
        #expect(TransactionType.expense.wire == "EXPENSE")
        #expect(PaymentStatus.pending.wire == "PENDING")
        #expect(PaymentMethod.pix.wire == "PIX")
    }
}
