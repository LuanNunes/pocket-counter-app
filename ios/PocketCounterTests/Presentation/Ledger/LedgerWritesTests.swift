import Testing

@testable import PocketCounter

@Suite("LedgerWrites")
struct LedgerWritesTests {
    private func write(_ phase: PaymentStatusWrite.Phase) -> PaymentStatusWrite {
        PaymentStatusWrite(ref: .current, phase: phase)
    }

    @Test("only a write that left without failing, and whose row shows the target, is announced")
    func completed() {
        let a = TransactionID(rawValue: "a")
        let b = TransactionID(rawValue: "b")
        let old = [a: write(.inFlight(.paid)), b: write(.inFlight(.pending))]
        let new = [b: write(.failed(.server))]

        #expect(LedgerWrites.completed(from: old, to: new, statusOf: { _ in .paid }) == [.paid])
    }

    /// `.sessionExpired` drops the write without recording a failure, and nothing was saved.
    @Test("a write dropped with the row still on its old status is not announced")
    func droppedIsNotCompleted() {
        let a = TransactionID(rawValue: "a")
        let old = [a: write(.inFlight(.paid))]

        #expect(LedgerWrites.completed(from: old, to: [:], statusOf: { _ in .pending }).isEmpty)
    }

    @Test("a write whose row vanished is not announced")
    func missingRowIsNotCompleted() {
        let a = TransactionID(rawValue: "a")
        let old = [a: write(.inFlight(.paid))]

        #expect(LedgerWrites.completed(from: old, to: [:], statusOf: { _ in nil }).isEmpty)
    }
}
