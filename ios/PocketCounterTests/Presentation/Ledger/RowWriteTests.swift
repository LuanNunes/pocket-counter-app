import Testing

@testable import PocketCounter

@Suite("RowWrite")
struct RowWriteTests {
    @Test("only a write in flight has a target")
    func target() {
        #expect(RowWrite(ref: .current, phase: .inFlight(PaymentStatus.paid)).target == .paid)
        #expect(RowWrite<PaymentStatus>(ref: .current, phase: .failed(.server)).target == nil)
        #expect(RowWrite<PaymentStatus>(ref: .current, phase: .settled(.paid)).target == nil)
    }

    @Test("a projection is the target of a write in flight or settled, never of a failed one")
    func projection() {
        #expect(RowWrite(ref: .current, phase: .inFlight(PaymentStatus.paid)).projection == .paid)
        #expect(RowWrite<PaymentStatus>(ref: .current, phase: .settled(.pending)).projection == .pending)
        #expect(RowWrite<PaymentStatus>(ref: .current, phase: .failed(.server)).projection == nil)
    }

    @Test("only a write in flight is in flight")
    func isInFlight() {
        #expect(RowWrite(ref: .current, phase: .inFlight(PaymentStatus.paid)).isInFlight)
        #expect(!RowWrite<PaymentStatus>(ref: .current, phase: .settled(.paid)).isInFlight)
        #expect(!RowWrite<PaymentStatus>(ref: .current, phase: .failed(.server)).isInFlight)
    }

    @Test("an intent's verb reads in flight, settled and failed")
    func verb() {
        #expect(RowIntentWrite(ref: .current, phase: .inFlight(.deletion)).verb == .deletion)
        #expect(RowIntentWrite(ref: .current, phase: .settled(.deletion)).verb == .deletion)
        #expect(RowIntentWrite(ref: .current, phase: .failed(.server), attempted: .fixo(true)).verb == .fixo(true))
    }

    @Test("an intent reads its fixo target; a deletion has none")
    func fixo() {
        #expect(RowIntent.fixo(true).fixo == true)
        #expect(RowIntent.fixo(false).fixo == false)
        #expect(RowIntent.deletion.fixo == nil)
    }
}
