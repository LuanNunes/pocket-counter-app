import Testing

@testable import PocketCounter

@Suite("ToggleFixo")
struct ToggleFixoTests {
    private let plain = HistoryItem.fixture(id: "t1", date: .of(2026, 10, 5), name: "Aluguel")
    private let fixo = HistoryItem.fixture(id: "t2", recurringTransactionId: "r7", name: "Luz")

    private func toggle(_ item: HistoryItem, _ fake: FakeRecurringTransactionRepository) async throws(WriteFailure) {
        try await ToggleFixo(recurring: fake).toggle(item)
    }

    @Test("a plain row creates its recurring transaction, then links to the one the server answered with")
    func makesFixo() async throws {
        let fake = FakeRecurringTransactionRepository()

        try await toggle(plain, fake)

        #expect(fake.log.calls == [
            .create(.makingFixo(plain)),
            .link(plain.id, RecurringTransactionID(rawValue: "created")),
        ])
    }

    @Test("a fixo row is unlinked from its own recurring transaction and creates nothing")
    func makesPlain() async throws {
        let fake = FakeRecurringTransactionRepository()

        try await toggle(fixo, fake)

        #expect(fake.log.calls == [.unlink(fixo.id, RecurringTransactionID(rawValue: "r7"))])
    }

    @Test("a failed create stops before linking")
    func createFails() async {
        var fake = FakeRecurringTransactionRepository()
        fake.createResult = .failure(.unreachable)

        await #expect(throws: WriteFailure.unreachable) { try await toggle(plain, fake) }
        #expect(fake.log.calls == [.create(.makingFixo(plain))])
    }

    @Test("a link that finds nothing is a failure: the recurring transaction does not exist")
    func linkVanished() async {
        var fake = FakeRecurringTransactionRepository()
        fake.linkResult = .failure(.vanished)

        await #expect(throws: WriteFailure.vanished) { try await toggle(plain, fake) }
    }

    @Test("an unlink that finds nothing is done: the row is already out of that recurring transaction")
    func unlinkVanished() async throws {
        var fake = FakeRecurringTransactionRepository()
        fake.unlinkResult = .failure(.vanished)

        try await toggle(fixo, fake)
    }

    @Test("any other unlink failure propagates", arguments: [
        WriteFailure.sessionExpired, .authenticationUnavailable, .unreachable, .rejected("x"), .server,
    ])
    func unlinkFails(failure: WriteFailure) async {
        var fake = FakeRecurringTransactionRepository()
        fake.unlinkResult = .failure(failure)

        await #expect(throws: failure) { try await toggle(fixo, fake) }
    }
}
