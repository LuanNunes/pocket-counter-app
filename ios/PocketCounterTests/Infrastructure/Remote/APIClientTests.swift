import Foundation
import Testing

@testable import PocketCounter

@Suite("APIClient")
struct APIClientTests {

    private struct Pong: Decodable, Sendable, Equatable { let message: String }

    private let base = URL(string: "https://api-dev.pocket-counter.com/")!
    private let ping = Endpoint<Pong>(method: .get, path: "api/v1/ping")

    private func client(_ fake: FakeHTTP) -> APIClient { APIClient(baseURL: base, send: fake.send) }

    @Test("a 200 decodes the body")
    func ok() async throws {
        let fake = FakeHTTP(FakeHTTP.json(#"{"message":"pong"}"#))

        let pong = try await client(fake).send(ping)

        #expect(pong == Pong(message: "pong"))
        #expect(fake.requests.first?.url?.absoluteString == "https://api-dev.pocket-counter.com/api/v1/ping")
    }

    @Test("a 201 is a success too")
    func created() async throws {
        let fake = FakeHTTP(FakeHTTP.json(#"{"message":"made"}"#, status: 201))

        let pong = try await client(fake).send(ping)

        #expect(pong == Pong(message: "made"))
    }

    @Test("a 204 with no body succeeds when the response is ignored")
    func noContent() async throws {
        let fake = FakeHTTP(FakeHTTP.empty(204))

        try await client(fake).sendIgnoringResponse(Endpoint<EmptyResponse>(method: .post, path: "api/v1/auth/logout"))

        #expect(fake.callCount == 1)
    }

    @Test("a 4xx with a server error body carries the message")
    func clientErrorWithBody() async {
        let fake = FakeHTTP(FakeHTTP.json(
            #"{"code":"VALIDATION","message":"Invalid","details":["email is required"],"correlationId":"abc-1"}"#,
            status: 422
        ))

        let error = await failure(of: ping, using: fake)

        let expected = ServerMessage(
            code: "VALIDATION", message: "Invalid", details: ["email is required"], correlationId: "abc-1"
        )
        guard case .status(let code, let server) = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(code == 422)
        #expect(server == expected)
    }

    @Test("a server error body without details or correlation id still parses")
    func sparseErrorBody() async {
        let fake = FakeHTTP(FakeHTTP.json(#"{"code":"BAD","message":"Nope"}"#, status: 400))

        let error = await failure(of: ping, using: fake)

        guard case .status(_, let server) = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(server == ServerMessage(code: "BAD", message: "Nope", details: [], correlationId: nil))
    }

    @Test("a 4xx without a body has no server message")
    func clientErrorWithoutBody() async {
        let fake = FakeHTTP(FakeHTTP.empty(401))

        let error = await failure(of: ping, using: fake)

        guard case .status(let code, let server) = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(code == 401)
        #expect(server == nil)
    }

    @Test("a 5xx with an HTML body has no server message")
    func serverErrorWithHTML() async {
        let fake = FakeHTTP(FakeHTTP.json("<html>Bad Gateway</html>", status: 502))

        let error = await failure(of: ping, using: fake)

        guard case .status(let code, let server) = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(code == 502)
        #expect(server == nil)
    }

    @Test("a body that does not match the response type is a decoding error naming the endpoint")
    func decodingFailure() async {
        let fake = FakeHTTP(FakeHTTP.json(#"{"unexpected":1}"#))

        let error = await failure(of: ping, using: fake)

        guard case .decoding(let endpoint, _) = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(endpoint == "GET api/v1/ping")
    }

    @Test("URLError.cancelled is its own case")
    func cancelled() async {
        let fake = FakeHTTP(.failure(URLError(.cancelled)))

        let error = await failure(of: ping, using: fake)

        guard case .cancelled = error else { Issue.record("got \(String(describing: error))"); return }
    }

    @Test("other transport failures keep the URLError")
    func transport() async {
        let fake = FakeHTTP(.failure(URLError(.notConnectedToInternet)))

        let error = await failure(of: ping, using: fake)

        guard case .transport(let urlError) = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(urlError.code == .notConnectedToInternet)
    }

    @Test("an invalid endpoint fails before sending")
    func invalidRequest() async {
        let fake = FakeHTTP(FakeHTTP.json("{}"))
        let bad = Endpoint<Pong>(method: .get, path: "/api/v1/ping")

        let error = await failure(of: bad, using: fake)

        guard case .invalidRequest = error else { Issue.record("got \(String(describing: error))"); return }
        #expect(fake.callCount == 0)
    }

    private func failure<R>(of endpoint: Endpoint<R>, using fake: FakeHTTP) async -> APIError? {
        do {
            _ = try await client(fake).send(endpoint)
            return nil
        } catch {
            return error
        }
    }
}
