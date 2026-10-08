import Foundation
import Testing

@testable import PocketCounter

@Suite("APISentenceReadingRepository")
struct APISentenceReadingRepositoryTests {
    private let sentence = "gastei 150 no supermercado"

    private func repository(_ http: FakeHTTP) -> APISentenceReadingRepository {
        APISentenceReadingRepository(client: AuthenticatedFixture.client(http.send))
    }

    private func read(_ http: FakeHTTP, on day: CalendarDay = .of(2026, 10, 7)) async throws -> SentenceReading {
        try await repository(http).reading(of: SentenceText(sentence), on: day)
    }

    @Test("the route names the backend path, bearer-authenticated, as a POST")
    func path() throws {
        let route = APISentenceReadingRepository.Route.reading(of: try SentenceText("x"), on: .fixture)

        #expect(route.path == "api/v1/transactions/raw")
        #expect(route.method == .post)
        #expect(route.authentication == .bearer)
    }

    @Test("it posts the sentence and the device's reference date, and maps the answer")
    func happyPath() async throws {
        let http = FakeHTTP(FakeHTTP.json(WireFixtures.Captured.supermarket))

        let reading = try await read(http)

        let request = try #require(http.requests.first)
        let body = try #require(request.httpBody)
        let sent = try JSONSerialization.jsonObject(with: body) as? [String: String]
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/api/v1/transactions/raw")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access")
        #expect(sent == ["text": "gastei 150 no supermercado", "referenceDate": "2026-10-07"])
        #expect(reading.amount?.value == Money(150))
    }

    @Test("a 400 is rejected with the resolved message in details, not a key", arguments: [
        (WireFixtures.Captured.blankText, "The text is required"),
        (WireFixtures.Captured.missingReferenceDate, "The reference date is required"),
    ])
    func badRequest(body: String, message: String) async {
        let http = FakeHTTP(FakeHTTP.json(body, status: 400))

        await #expect(throws: ReadingFailure.rejected(message)) { try await read(http) }
    }

    @Test("a 401 is a lost session")
    func unauthorized() async {
        await #expect(throws: ReadingFailure.sessionExpired) { try await read(FakeHTTP(FakeHTTP.empty(401))) }
    }

    @Test("a 429 is told apart from a server failure")
    func rateLimited() async {
        await #expect(throws: ReadingFailure.tooManyRequests) { try await read(FakeHTTP(FakeHTTP.empty(429))) }
    }

    @Test("a 5xx is a server failure")
    func serverError() async {
        await #expect(throws: ReadingFailure.server) { try await read(FakeHTTP(FakeHTTP.empty(503))) }
    }

    @Test("a transport failure is unreachable")
    func transport() async {
        let http = FakeHTTP(.failure(URLError(.notConnectedToInternet)))

        await #expect(throws: ReadingFailure.unreachable) { try await read(http) }
    }

    @Test("an answer the mapper refuses is a server failure")
    func unmappable() async {
        let http = FakeHTTP(FakeHTTP.json(WireFixtures.rawResponse(missing: #"["TAG"]"#)))

        await #expect(throws: ReadingFailure.server) { try await read(http) }
    }
}
