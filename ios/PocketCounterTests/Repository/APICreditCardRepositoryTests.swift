import Foundation
import Testing

@testable import PocketCounter

@Suite("APICreditCardRepository")
struct APICreditCardRepositoryTests {
    private func list(_ rows: String...) -> FakeHTTP.Reply {
        FakeHTTP.json("[" + rows.joined(separator: ",") + "]")
    }

    private func repository(_ http: FakeHTTP) -> APICreditCardRepository {
        APICreditCardRepository(client: AuthenticatedFixture.client(http.send))
    }

    @Test("the route names the backend path, bearer-authenticated")
    func path() {
        #expect(APICreditCardRepository.Route.cards.path == "api/v1/credit-cards")
        #expect(APICreditCardRepository.Route.cards.authentication == .bearer)
    }

    @Test("cards sort by name the way the user reads it, ties broken by id")
    func sortedByName() async throws {
        let http = FakeHTTP(list(
            WireFixtures.card(id: "3", name: "Nubank"),
            WireFixtures.card(id: "2", name: "Itaú"),
            WireFixtures.card(id: "1", name: "Nubank")
        ))

        let cards = try await repository(http).cards()

        #expect(cards.map(\.id.rawValue) == ["2", "1", "3"])
    }

    @Test("a repeated read is served from the cache until invalidated")
    func caches() async throws {
        let http = FakeHTTP(list(WireFixtures.card()))
        let repository = repository(http)

        _ = try await repository.cards()
        _ = try await repository.cards()
        #expect(http.callCount == 1)

        await repository.invalidateLookups()
        _ = try await repository.cards()
        #expect(http.callCount == 2)
    }

    @Test("a 401 is a lost session")
    func unauthorized() async {
        let http = FakeHTTP(FakeHTTP.empty(401))

        await #expect(throws: LoadFailure.sessionExpired) { try await repository(http).cards() }
    }
}
