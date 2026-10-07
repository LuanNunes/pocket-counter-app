import Testing

@testable import PocketCounter

@Suite("TransactionRowWrite")
struct TransactionRowWriteTests {
    private func write(_ phase: PaymentStatusWrite.Phase) -> PaymentStatusWrite {
        PaymentStatusWrite(ref: .current, phase: phase)
    }

    @Test("in flight is busy and silent")
    func inFlight() {
        #expect(TransactionRowWrite.of(write(.inFlight(.paid))) == .init(isBusy: true, notice: nil, remedy: nil))
    }

    @Test("a vanished row offers Atualizar")
    func vanished() {
        let row = TransactionRowWrite.of(write(.failed(.vanished)))
        #expect(row.remedy == .refresh)
        #expect(row.isBusy == false)
        #expect(row.notice != nil)
    }

    @Test("an unreachable server offers Tentar novamente")
    func unreachable() {
        let row = TransactionRowWrite.of(write(.failed(.unreachable)))
        #expect(row.remedy == .retry)
        #expect(row.notice != nil)
    }

    @Test("an expired session shows nothing")
    func sessionExpired() {
        #expect(TransactionRowWrite.of(write(.failed(.sessionExpired))) == .none)
    }

    @Test("no write shows nothing")
    func absent() {
        #expect(TransactionRowWrite.of(nil) == .none)
    }

    @Test("only a write that left without failing, and whose row shows the target, is announced")
    func completed() {
        let a = TransactionID(rawValue: "a")
        let b = TransactionID(rawValue: "b")
        let old = [a: write(.inFlight(.paid)), b: write(.inFlight(.pending))]
        let new = [b: write(.failed(.server))]

        #expect(TransactionRowWrite.completed(from: old, to: new, statusOf: { _ in .paid }) == [.paid])
    }

    /// `.sessionExpired` drops the write without recording a failure, and nothing was saved.
    @Test("a write dropped with the row still on its old status is not announced")
    func droppedIsNotCompleted() {
        let a = TransactionID(rawValue: "a")
        let old = [a: write(.inFlight(.paid))]

        #expect(TransactionRowWrite.completed(from: old, to: [:], statusOf: { _ in .pending }).isEmpty)
    }

    @Test("a write whose row vanished is not announced")
    func missingRowIsNotCompleted() {
        let a = TransactionID(rawValue: "a")
        let old = [a: write(.inFlight(.paid))]

        #expect(TransactionRowWrite.completed(from: old, to: [:], statusOf: { _ in nil }).isEmpty)
    }
}
