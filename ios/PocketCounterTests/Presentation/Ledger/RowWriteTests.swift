import Testing

@testable import PocketCounter

@Suite("RowWrite")
struct RowWriteTests {
    @Test("only a write in flight has a target")
    func target() {
        #expect(RowWrite(ref: .current, phase: .inFlight(PaymentStatus.paid)).target == .paid)
        #expect(RowWrite<PaymentStatus>(ref: .current, phase: .failed(.server)).target == nil)
    }

    @Test("an intent reads its fixo target; a deletion has none")
    func fixo() {
        #expect(RowIntent.fixo(true).fixo == true)
        #expect(RowIntent.fixo(false).fixo == false)
        #expect(RowIntent.deletion.fixo == nil)
    }
}
