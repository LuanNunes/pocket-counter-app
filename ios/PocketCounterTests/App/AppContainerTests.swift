import Foundation
import Testing

@testable import PocketCounter

@MainActor
@Suite("AppContainer")
struct AppContainerTests {

    private func container(_ environment: String, keychain: FakeKeychain, send: FakeHTTP = FakeHTTP(FakeHTTP.empty(500))) throws -> AppContainer {
        let configuration = try AppConfiguration(environmentName: environment, baseURLString: "https://h.com/")
        return AppContainer.forTesting(configuration: configuration, keychain: keychain.access, send: send.send)
    }

    @Test("the Keychain service is scoped by environment", arguments: ["local", "dev", "prod"])
    func scopesKeychainByEnvironment(environment: String) async throws {
        let keychain = FakeKeychain.missing()

        _ = try await container(environment, keychain: keychain).sessionRepository.restore()

        #expect(keychain.services == ["com.resolveprogramming.pocketcounter.tokens.\(environment)"])
    }

    @Test("a sign-in is visible to a restore on the same container: one token store")
    func sharedStore() async throws {
        let token = JWTFixture.token(email: "ana@b.com", name: "Ana")
        let http = FakeHTTP(FakeHTTP.json(#"{"accessToken":"\#(token)","refreshToken":"r1","expiresIn":900,"tokenType":"Bearer"}"#))
        let keychain = FakeKeychain.missing()
        let container = try container("dev", keychain: keychain, send: http)
        let credentials = try LoginCredentials(email: "ana@b.com", password: "secret")

        let user = try await container.sessionRepository.signIn(credentials)
        let restored = await container.sessionRepository.restore()

        #expect(restored == .signedIn(user))
        #expect(keychain.readCount == 0)
    }

    @Test("two reads of a lookup repository share one cache")
    func sharedCache() async throws {
        let http = FakeHTTP(routes: ["/api/v1/tags": FakeHTTP.json("[]")])
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)

        _ = try await container.tagRepository.tags()
        _ = try await container.tagRepository.tags()

        #expect(http.callCount == 1)
    }

    @Test("after a sign-out the next lookup read goes to the network, never to the previous user's cache")
    func signOutDropsLookupCaches() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/tags": FakeHTTP.json("[]"),
            "/api/v1/categories": FakeHTTP.json("[]"),
            "/api/v1/credit-cards": FakeHTTP.json("[]"),
        ])
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)
        let model = SessionModel(
            repository: FakeSessionRepository(signIn: .success(.fixture)), onSessionEnd: container.endSession
        )
        try await model.signIn(LoginCredentials(email: "ana@b.com", password: "secret"))
        _ = try await container.tagRepository.tags()
        _ = try await container.tagRepository.categories()
        _ = try await container.creditCardRepository.cards()
        #expect(http.callCount == 3)

        await model.signOut()
        _ = try await container.tagRepository.tags()
        _ = try await container.tagRepository.categories()
        _ = try await container.creditCardRepository.cards()

        #expect(http.callCount == 6)
    }

    @Test("the ledger use case reads through the same repositories")
    func loadLedgerSharesCaches() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/incomes/202610": FakeHTTP.json("[]"),
            "/api/v1/transactions/expenses/202610": FakeHTTP.json("[]"),
            "/api/v1/tags": FakeHTTP.json("[]"),
            "/api/v1/categories": FakeHTTP.json("[]"),
            "/api/v1/credit-cards": FakeHTTP.json("[]"),
        ])
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)
        let ref = try #require(RefYearMonth(raw: 202610))

        let first = try await container.loadLedger.month(ref)
        _ = try await container.loadLedger.month(ref)

        #expect(first.lookups.failed.isEmpty)
        #expect(http.callCount == 2 + 3 + 2)
    }
}
