import Foundation
import Testing

@testable import PocketCounter

@MainActor
@Suite("Lookup retry")
struct LookupRetryTests {

    @Test("retrying after a failed cards lookup re-requests only the cards")
    func retryOnlyCards() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/incomes/202610": FakeHTTP.json("[]"),
            "/api/v1/transactions/expenses/202610": FakeHTTP.json("[]"),
            "/api/v1/tags": FakeHTTP.json("[]"),
            "/api/v1/categories": FakeHTTP.json("[]"),
            "/api/v1/credit-cards": FakeHTTP.empty(500),
        ])
        let configuration = try AppConfiguration(environmentName: "dev", baseURLString: "https://h.com/")
        let keychain = FakeKeychain.holding(TokenPair(accessToken: "a", refreshToken: "r"))
        let container = AppContainer.forTesting(configuration: configuration, keychain: keychain.access, send: http.send)
        let ref = try #require(RefYearMonth(raw: 202610))
        let degraded = try await container.loadMonth(ref)
        http.reply(to: "/api/v1/credit-cards", with: FakeHTTP.json("[]"))

        let retried = try await container.loadMonth(ref)

        #expect(degraded.lookups.failed == [.cards])
        #expect(retried.lookups.failed.isEmpty)
        #expect(http.count(path: "/api/v1/credit-cards") == 2)
        #expect(http.count(path: "/api/v1/tags") == 1)
        #expect(http.count(path: "/api/v1/categories") == 1)
    }
}
