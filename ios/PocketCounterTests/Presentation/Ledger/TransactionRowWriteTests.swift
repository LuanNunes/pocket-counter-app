import Testing

@testable import PocketCounter

@Suite("TransactionRowWrite")
struct TransactionRowWriteTests {
    private func write(_ phase: PaymentStatusWrite.Phase) -> PaymentStatusWrite {
        PaymentStatusWrite(ref: .current, phase: phase)
    }

    @Test("in flight is busy and silent")
    func inFlight() {
        #expect(TransactionRowWrite.of(write(.inFlight(.paid)), subject: .saving) == .init(isBusy: true, notice: nil, remedy: nil))
    }

    @Test("a vanished row offers Atualizar")
    func vanished() {
        let row = TransactionRowWrite.of(write(.failed(.vanished)), subject: .saving)
        #expect(row.remedy == .refresh)
        #expect(row.isBusy == false)
        #expect(row.notice != nil)
    }

    @Test("an unreachable server offers Tentar novamente")
    func unreachable() {
        let row = TransactionRowWrite.of(write(.failed(.unreachable)), subject: .saving)
        #expect(row.remedy == .retry)
        #expect(row.notice != nil)
    }

    @Test("an expired session shows nothing")
    func sessionExpired() {
        #expect(TransactionRowWrite.of(write(.failed(.sessionExpired)), subject: .saving) == .none)
    }

    @Test("no write shows nothing")
    func absent() {
        #expect(TransactionRowWrite.of(nil as PaymentStatusWrite?, subject: .saving) == .none)
    }

    @Test("an intent in flight is busy whatever it asks for", arguments: [RowIntent.deletion, .fixo(true)])
    func intentInFlight(intent: RowIntent) {
        let write = RowIntentWrite(ref: .current, phase: .inFlight(intent))

        #expect(TransactionRowWrite.of(write, subject: .deleting) == .init(isBusy: true, notice: nil, remedy: nil))
    }

    @Test("a failed intent titles its notice by the subject")
    func intentFailed() {
        let write = RowIntentWrite(ref: .current, phase: .failed(.unreachable))

        #expect(TransactionRowWrite.of(write, subject: .deleting).notice?.title == "Não foi possível excluir")
        #expect(TransactionRowWrite.of(write, subject: .saving).notice?.title == "Não foi possível salvar")
    }

    @Test("an intent picks its subject from the verb it was running", arguments: [
        (RowIntent.deletion, "Não foi possível excluir"), (.fixo(true), "Não foi possível salvar"),
    ])
    func intentVerbTitlesFailure(intent: RowIntent, title: String) {
        let write = RowIntentWrite(ref: .current, phase: .failed(.unreachable), attempted: intent)

        #expect(TransactionRowWrite.of(write).notice?.title == title)
        #expect(TransactionRowWrite.of(RowIntentWrite(ref: .current, phase: .inFlight(intent))).isBusy)
        #expect(TransactionRowWrite.of(nil as RowIntentWrite?) == .none)
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
