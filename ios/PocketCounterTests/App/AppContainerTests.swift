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

    @Test("the sentence repository reads through the container's authenticated client")
    func sentenceReading() async throws {
        let http = FakeHTTP(routes: ["/api/v1/transactions/raw": FakeHTTP.json(WireFixtures.Captured.supermarket)])
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)

        _ = try await container.sentenceReadingRepository.reading(of: try SentenceText("gastei 150"), on: .fixture)

        #expect(http.requests.first?.value(forHTTPHeaderField: "Authorization") == "Bearer a")
        #expect(http.count(path: "/api/v1/transactions/raw") == 1)
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

    @Test("the month action reads through the same caches as the use case")
    func loadMonthSharesCaches() async throws {
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

        _ = try await container.loadMonth(ref)
        let second = try await container.loadMonth(ref)

        #expect(second.ref == ref)
        #expect(http.callCount == 2 + 3 + 2)
    }

    @Test("the status action issues one authenticated PUT to the status path")
    func setPaymentStatusAction() async throws {
        let http = FakeHTTP(FakeHTTP.json(#""t1""#))
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)

        try await container.setPaymentStatus(TransactionID(rawValue: "t1"), .pending)

        #expect(http.callCount == 1)
        #expect(http.requests.first?.httpMethod == "PUT")
        #expect(http.requests.first?.url?.path == "/api/v1/transactions/t1/pending")
        #expect(http.requests.first?.value(forHTTPHeaderField: "Authorization") == "Bearer a")
    }

    @Test("the delete action issues one authenticated DELETE to the transaction")
    func deleteAction() async throws {
        let http = FakeHTTP(FakeHTTP.empty(200))
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)

        try await container.deleteTransaction(TransactionID(rawValue: "t1"))

        #expect(http.requests.map(\.httpMethod) == ["DELETE"])
        #expect(http.requests.first?.url?.path == "/api/v1/transactions/t1")
        #expect(http.requests.first?.value(forHTTPHeaderField: "Authorization") == "Bearer a")
    }

    @Test("the reorder action issues one authenticated PUT to the reorder path")
    func reorderAction() async throws {
        let http = FakeHTTP(FakeHTTP.empty(200))
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)

        try await container.reorderTransactions([TransactionID(rawValue: "t1"), TransactionID(rawValue: "t2")])

        #expect(http.requests.map(\.httpMethod) == ["PUT"])
        #expect(http.requests.first?.url?.path == "/api/v1/transactions/reorder")
        #expect(http.requests.first?.value(forHTTPHeaderField: "Authorization") == "Bearer a")
    }

    @Test("the fixo action on a plain row creates a series, then links the row to it")
    func toggleFixoAction() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/recurring-series": FakeHTTP.json(WireFixtures.series(id: "s9")),
            "/api/v1/recurring-series/s9/transactions/t1": FakeHTTP.empty(200),
        ])
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)

        try await container.toggleFixo(.fixture(id: "t1", name: "Aluguel"))

        #expect(http.requests.map { "\($0.httpMethod ?? "") \($0.url?.path ?? "")" } == [
            "POST /api/v1/recurring-series",
            "POST /api/v1/recurring-series/s9/transactions/t1",
        ])
    }

    @Test("a retry after a degraded lookup re-requests only that lookup and the transactions")
    func degradedRetry() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/incomes/202610": FakeHTTP.json("[]"),
            "/api/v1/transactions/expenses/202610": FakeHTTP.json("[]"),
            "/api/v1/tags": FakeHTTP.empty(500),
            "/api/v1/categories": FakeHTTP.json("[]"),
            "/api/v1/credit-cards": FakeHTTP.json("[]"),
        ])
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = try container("dev", keychain: keychain, send: http)
        let ref = try #require(RefYearMonth(raw: 202610))
        let degraded = try await container.loadMonth(ref)
        http.reply(to: "/api/v1/tags", with: FakeHTTP.json("[]"))

        let retried = try await container.loadMonth(ref)

        #expect(degraded.lookups.failed == [.tags])
        #expect(retried.lookups.failed.isEmpty)
        #expect(http.count(path: "/api/v1/tags") == 2)
        #expect(http.count(path: "/api/v1/categories") == 1)
        #expect(http.count(path: "/api/v1/credit-cards") == 1)
        #expect(http.count(path: "/api/v1/transactions/incomes/202610") == 2)
        #expect(http.count(path: "/api/v1/transactions/expenses/202610") == 2)
    }
}
