import Foundation
import Testing

@testable import PocketCounter

@Suite("AuthenticatedAPIClient")
struct AuthenticatedAPIClientTests {

    private struct Pong: Decodable, Sendable, Equatable { let message: String }

    private let base = URL(string: "https://api-dev.pocket-counter.com/")!
    private let stored = TokenPair(accessToken: "old-access", refreshToken: "old-refresh")
    private let ping = Endpoint<Pong>(method: .get, path: "api/v1/ping", authentication: .bearer)
    private static let rotated = #"{"accessToken":"new-access","refreshToken":"new-refresh","expiresIn":900,"tokenType":"Bearer"}"#
    private static let pong = #"{"message":"pong"}"#

    private func make(_ send: @escaping HTTPSend, tokens: InMemoryTokenStore) -> AuthenticatedAPIClient {
        let raw = APIClient(baseURL: base, send: send)
        return AuthenticatedAPIClient(
            client: raw, tokens: tokens, refresher: TokenRefresher(client: raw, tokens: tokens)
        )
    }

    private func bearer(_ request: URLRequest?) -> String? {
        request?.value(forHTTPHeaderField: "Authorization")
    }

    @Test("the stored access token is attached as a bearer")
    func attachesBearer() async throws {
        let fake = FakeHTTP(FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        let pong = try await client.send(ping)

        #expect(pong == Pong(message: "pong"))
        #expect(bearer(fake.requests.first) == "Bearer old-access")
    }

    @Test("a bearer endpoint with no stored token is never sent")
    func noToken() async {
        let fake = FakeHTTP(FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(nil))

        let error = await apiError(of: ping, using: client)

        #expect(error == .sessionExpired)
        #expect(fake.callCount == 0)
    }

    @Test("a credentials endpoint with no stored token is still sent, without a bearer")
    func noTokenCredentials() async throws {
        let fake = FakeHTTP(FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(nil))
        let login = Endpoint<Pong>(method: .post, path: "api/v1/auth/login", authentication: .credentials)

        let pong = try await client.send(login)

        #expect(pong == Pong(message: "pong"))
        #expect(bearer(fake.requests.first) == nil)
    }

    @Test("a 401 refreshes the token and retries once with the new bearer")
    func refreshesAndRetries() async throws {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated), FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        let pong = try await client.send(ping)

        #expect(pong == Pong(message: "pong"))
        #expect(fake.requests.map(\.url?.path) == ["/api/v1/ping", "/api/v1/auth/refresh", "/api/v1/ping"])
        #expect(bearer(fake.requests.last) == "Bearer new-access")
    }

    @Test("a 401 right after a refresh ends the session and reports it expired, with no second refresh")
    func secondUnauthorized() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated), FakeHTTP.empty(401))
        let tokens = InMemoryTokenStore(stored)
        let client = make(fake.send, tokens: tokens)

        let error = await apiError(of: ping, using: client)

        #expect(error == .sessionExpired)
        #expect(await tokens.stored == nil)
        #expect(fake.callCount == 3)
    }

    @Test("a refresh refused with 401 ends the session and reports it expired")
    func refreshRefused() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.empty(401))
        let tokens = InMemoryTokenStore(stored)
        let client = make(fake.send, tokens: tokens)

        let error = await apiError(of: ping, using: client)

        #expect(error == .sessionExpired)
        #expect(await tokens.stored == nil)
        #expect(fake.callCount == 2)
    }

    @Test("a refresh that hits a server error reports authentication unavailable and keeps the session")
    func refreshServerError() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.empty(500))
        let tokens = InMemoryTokenStore(stored)
        let client = make(fake.send, tokens: tokens)

        let error = await apiError(of: ping, using: client)

        #expect(error == .authenticationUnavailable)
        #expect(await tokens.stored == stored)
        #expect(fake.callCount == 2)
    }

    @Test("a refresh that cannot reach the server reports authentication unavailable and keeps the session")
    func refreshOffline() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), .failure(URLError(.notConnectedToInternet)))
        let tokens = InMemoryTokenStore(stored)
        let client = make(fake.send, tokens: tokens)

        let error = await apiError(of: ping, using: client)

        #expect(error == .authenticationUnavailable)
        #expect(await tokens.stored == stored)
    }

    @Test("a 401 from an auth endpoint never triggers a refresh")
    func authEndpointDoesNotRefresh() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        let code = await statusCode(of: Endpoint<Pong>(method: .post, path: "api/v1/auth/login", authentication: .credentials), using: client)

        #expect(code == 401)
        #expect(fake.callCount == 1)
    }

    @Test("a bearer endpoint under /auth/ still refreshes and retries")
    func bearerAuthEndpointRefreshes() async throws {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated), FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))
        let providers = Endpoint<Pong>(method: .get, path: "api/v1/auth/providers", authentication: .bearer)

        _ = try await client.send(providers)

        #expect(fake.requests.map(\.url?.path) == ["/api/v1/auth/providers", "/api/v1/auth/refresh", "/api/v1/auth/providers"])
        #expect(bearer(fake.requests.last) == "Bearer new-access")
    }

    @Test("a burst of six 401s causes one refresh and twelve sends of the original request")
    func burst() async throws {
        let counts = PathCounter()
        let send: HTTPSend = { request in
            let path = request.url?.path ?? ""
            counts.record(path)
            let url = try #require(request.url)
            func reply(_ status: Int, _ body: String) -> (Data, HTTPURLResponse) {
                (Data(body.utf8), HTTPURLResponse(url: url, statusCode: status, httpVersion: nil, headerFields: nil) ?? HTTPURLResponse())
            }
            if path.hasSuffix("/auth/refresh") {
                try await Task.sleep(for: .milliseconds(100))
                return reply(200, Self.rotated)
            }
            let authorized = request.value(forHTTPHeaderField: "Authorization") == "Bearer new-access"
            try await Task.sleep(for: .milliseconds(20))
            return authorized ? reply(200, Self.pong) : reply(401, "")
        }
        let client = make(send, tokens: InMemoryTokenStore(stored))

        let pongs = try await withThrowingTaskGroup(of: Pong.self) { group in
            for _ in 0..<6 { group.addTask { try await client.send(ping) } }
            return try await group.reduce(into: []) { $0.append($1) }
        }

        #expect(pongs.count == 6)
        #expect(counts.count("/api/v1/auth/refresh") == 1)
        #expect(counts.count("/api/v1/ping") == 12)
    }

    @Test("a body-less endpoint is retried after a refresh too")
    func ignoringResponse() async throws {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated), FakeHTTP.empty(204))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        try await client.sendIgnoringResponse(Endpoint<EmptyResponse>(method: .delete, path: "api/v1/things/1", authentication: .bearer))

        #expect(fake.callCount == 3)
    }

    private final class PathCounter: @unchecked Sendable {
        private let lock = NSLock()
        private var paths: [String] = []
        func record(_ path: String) { lock.withLock { paths.append(path) } }
        func count(_ path: String) -> Int { lock.withLock { paths.filter { $0 == path }.count } }
    }

    private func statusCode<R>(of endpoint: Endpoint<R>, using client: AuthenticatedAPIClient) async -> Int? {
        do {
            _ = try await client.send(endpoint)
            return nil
        } catch {
            guard case .status(let code, _) = error else { return nil }
            return code
        }
    }

    private func apiError<R>(of endpoint: Endpoint<R>, using client: AuthenticatedAPIClient) async -> APIError? {
        do {
            _ = try await client.send(endpoint)
            return nil
        } catch {
            return error
        }
    }

    @Test("a failing read reports authentication unavailable before any request goes out")
    func unreadableStore() async {
        let fake = FakeHTTP(FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored, failingReads: 1))

        let error = await apiError(of: ping, using: client)

        #expect(error == .authenticationUnavailable)
        #expect(fake.callCount == 0)
    }

    @Test("a 401 whose refresh succeeds but cannot be persisted still retries with the new bearer")
    func rotationNotPersisted() async throws {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated), FakeHTTP.json(Self.pong))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored, failingWrites: true))

        let pong = try await client.send(ping)

        #expect(pong == Pong(message: "pong"))
        #expect(bearer(fake.requests.last) == "Bearer new-access")
    }
}
