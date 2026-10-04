import Foundation
import Testing

@testable import PocketCounter

@Suite("AuthenticatedAPIClient")
struct AuthenticatedAPIClientTests {

    private struct Pong: Decodable, Sendable, Equatable { let message: String }

    private let base = URL(string: "https://api-dev.pocket-counter.com/")!
    private let stored = TokenPair(accessToken: "old-access", refreshToken: "old-refresh")
    private let ping = Endpoint<Pong>(method: .get, path: "api/v1/ping")
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

    @Test("without a stored token the request goes out with no Authorization header")
    func noToken() async {
        let fake = FakeHTTP(FakeHTTP.empty(401))
        let client = make(fake.send, tokens: InMemoryTokenStore(nil))

        let error = await statusCode(of: ping, using: client)

        #expect(error == 401)
        #expect(bearer(fake.requests.first) == nil)
        #expect(fake.callCount == 1)
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

    @Test("a second 401 is returned as is, with no second refresh")
    func secondUnauthorized() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated), FakeHTTP.empty(401))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        let code = await statusCode(of: ping, using: client)

        #expect(code == 401)
        #expect(fake.callCount == 3)
    }

    @Test("when the refresh fails the original 401 comes back and nothing is retried", arguments: [401, 500])
    func refreshFails(status: Int) async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.empty(status))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        let code = await statusCode(of: ping, using: client)

        #expect(code == 401)
        #expect(fake.callCount == 2)
    }

    @Test("a 401 from an auth endpoint never triggers a refresh")
    func authEndpointDoesNotRefresh() async {
        let fake = FakeHTTP(FakeHTTP.empty(401), FakeHTTP.json(Self.rotated))
        let client = make(fake.send, tokens: InMemoryTokenStore(stored))

        let code = await statusCode(of: Endpoint<Pong>(method: .post, path: "api/v1/auth/login"), using: client)

        #expect(code == 401)
        #expect(fake.callCount == 1)
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

        try await client.sendIgnoringResponse(Endpoint<EmptyResponse>(method: .delete, path: "api/v1/things/1"))

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
}
