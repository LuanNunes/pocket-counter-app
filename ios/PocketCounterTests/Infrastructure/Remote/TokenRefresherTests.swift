import Foundation
import Testing

@testable import PocketCounter

@Suite("TokenRefresher")
struct TokenRefresherTests {

    private let base = URL(string: "https://api-dev.pocket-counter.com/")!
    private let stored = TokenPair(accessToken: "old-access", refreshToken: "old-refresh")
    private static let rotated = #"{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900,"tokenType":"Bearer"}"#

    /// The delay keeps the first refresh in flight while the other callers arrive.
    private func slowRefresher(_ fake: FakeHTTP, tokens: InMemoryTokenStore) -> TokenRefresher {
        let send: HTTPSend = { request in
            try await Task.sleep(for: .milliseconds(100))
            return try await fake.send(request)
        }
        return TokenRefresher(client: APIClient(baseURL: base, send: send), tokens: tokens)
    }

    private func refresher(_ fake: FakeHTTP, tokens: InMemoryTokenStore) -> TokenRefresher {
        TokenRefresher(client: APIClient(baseURL: base, send: fake.send), tokens: tokens)
    }

    @Test("without a stored refresh token the session is invalid and nothing goes over the network")
    func noRefreshToken() async {
        let fake = FakeHTTP(FakeHTTP.json(Self.rotated))
        let tokens = InMemoryTokenStore(nil)

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .sessionInvalid)
        #expect(fake.callCount == 0)
    }

    @Test("a successful refresh posts the refresh token as `token`, stores the pair and returns the new access token")
    func success() async throws {
        let fake = FakeHTTP(FakeHTTP.json(Self.rotated))
        let tokens = InMemoryTokenStore(stored)

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .refreshed("new-access"))
        #expect(await tokens.tokens() == TokenPair(accessToken: "new-access", refreshToken: "new-refresh"))
        let request = try #require(fake.requests.first)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/api/v1/auth/refresh")
        #expect(request.httpBody.flatMap { String(data: $0, encoding: .utf8) } == #"{"token":"old-refresh"}"#)
    }

    @Test("a caller holding a stale token gets the already-rotated one without network")
    func alreadyRotated() async {
        let fake = FakeHTTP(FakeHTTP.json(Self.rotated))
        let tokens = InMemoryTokenStore(TokenPair(accessToken: "rotated-by-someone", refreshToken: "r"))

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .refreshed("rotated-by-someone"))
        #expect(fake.callCount == 0)
    }

    @Test("a caller with no token of its own still refreshes")
    func nilStaleToken() async {
        let fake = FakeHTTP(FakeHTTP.json(Self.rotated))

        let outcome = await refresher(fake, tokens: InMemoryTokenStore(stored)).accessToken(replacing: nil)

        #expect(outcome == .refreshed("new-access"))
        #expect(fake.callCount == 1)
    }

    @Test("a rejected refresh token ends the session and clears the tokens", arguments: [401, 403])
    func rejected(status: Int) async {
        let fake = FakeHTTP(FakeHTTP.json(#"{"code":"UNAUTHORIZED","message":"Invalid refresh token"}"#, status: status))
        let tokens = InMemoryTokenStore(stored)

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .sessionInvalid)
        #expect(await tokens.tokens() == nil)
    }

    @Test("a 5xx keeps the session")
    func serverError() async {
        let fake = FakeHTTP(FakeHTTP.empty(500))
        let tokens = InMemoryTokenStore(stored)

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .unavailable)
        #expect(await tokens.tokens() == stored)
    }

    @Test("being offline keeps the session")
    func offline() async {
        let fake = FakeHTTP(.failure(URLError(.notConnectedToInternet)))
        let tokens = InMemoryTokenStore(stored)

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .unavailable)
        #expect(await tokens.tokens() == stored)
    }

    @Test("an unreadable 200 keeps the session")
    func undecodable() async {
        let fake = FakeHTTP(FakeHTTP.json(#"{"oops":true}"#))
        let tokens = InMemoryTokenStore(stored)

        let outcome = await refresher(fake, tokens: tokens).accessToken(replacing: "old-access")

        #expect(outcome == .unavailable)
        #expect(await tokens.tokens() == stored)
    }

    @Test("ten concurrent callers cause exactly one refresh request and all receive the new token")
    func singleFlight() async {
        let fake = FakeHTTP(FakeHTTP.json(Self.rotated))
        let refresher = slowRefresher(fake, tokens: InMemoryTokenStore(stored))

        let outcomes = await withTaskGroup(of: TokenRefresher.Outcome.self) { group in
            for _ in 0..<10 { group.addTask { await refresher.accessToken(replacing: "old-access") } }
            return await group.reduce(into: []) { $0.append($1) }
        }

        #expect(fake.callCount == 1)
        #expect(outcomes == Array(repeating: .refreshed("new-access"), count: 10))
    }

    @Test("cancelling the caller that started the refresh does not stop the callers riding on it")
    func creatorCancelled() async throws {
        let fake = FakeHTTP(FakeHTTP.json(Self.rotated))
        let refresher = slowRefresher(fake, tokens: InMemoryTokenStore(stored))

        let creator = Task { await refresher.accessToken(replacing: "old-access") }
        try await Task.sleep(for: .milliseconds(20))
        let passengers = (0..<3).map { _ in Task { await refresher.accessToken(replacing: "old-access") } }
        creator.cancel()

        for passenger in passengers {
            #expect(await passenger.value == .refreshed("new-access"))
        }
        #expect(fake.callCount == 1)
    }

    @Test("a refresh that finished leaves room for a later one")
    func sequentialRefreshes() async {
        let fake = FakeHTTP(
            FakeHTTP.json(Self.rotated),
            FakeHTTP.json(#"{"accessToken":"third","refreshToken":"third-r","expiresIn":900,"tokenType":"Bearer"}"#)
        )
        let refresher = refresher(fake, tokens: InMemoryTokenStore(stored))

        _ = await refresher.accessToken(replacing: "old-access")
        let second = await refresher.accessToken(replacing: "new-access")

        #expect(second == .refreshed("third"))
        #expect(fake.callCount == 2)
    }
}
