import Foundation
import Testing

@testable import PocketCounter

@Suite("APIRecurringSeriesRepository routes")
struct APIRecurringSeriesRepositoryRouteTests {
    private typealias Route = APIRecurringSeriesRepository.Route
    private let series = SeriesID(rawValue: "s1")
    private let transaction = TransactionID(rawValue: "t1")

    @Test("each route names the backend path with no leading or trailing slash, bearer-authenticated")
    func paths() {
        let draft = RecurringSeriesDraft(name: "Aluguel", type: .expense, recurrenceDay: 5)
        let create = Route.create(draft)
        let link = Route.link(transaction, to: series)
        let unlink = Route.unlink(transaction, from: series)

        #expect([create.path, link.path, unlink.path] == [
            "api/v1/recurring-series",
            "api/v1/recurring-series/s1/transactions/t1",
            "api/v1/recurring-series/s1/transactions/t1",
        ])
        #expect([create.method.rawValue, link.method.rawValue, unlink.method.rawValue] == ["POST", "POST", "DELETE"])
        #expect(create.authentication == .bearer)
        #expect(link.authentication == .bearer)
        #expect(unlink.authentication == .bearer)
        #expect(link.body == nil)
        #expect(link.query.isEmpty)
    }
}

@Suite("APIRecurringSeriesRepository")
struct APIRecurringSeriesRepositoryTests {
    private let series = SeriesID(rawValue: "s1")
    private let transaction = TransactionID(rawValue: "t1")
    private let draft = RecurringSeriesDraft(name: "Aluguel", type: .expense, recurrenceDay: 5)

    private func repository(_ http: FakeHTTP) -> APIRecurringSeriesRepository {
        APIRecurringSeriesRepository(client: AuthenticatedFixture.client(http.send))
    }

    @Test("creating posts the draft and maps the series the server answers with")
    func create() async throws {
        let http = FakeHTTP(FakeHTTP.json(WireFixtures.series(id: "s9")))

        let created = try await repository(http).create(draft)

        #expect(created == RecurringSeries(id: SeriesID(rawValue: "s9"), name: "Aluguel", type: .expense, recurrenceDay: 5))
        let request = try #require(http.requests.first)
        #expect(request.httpMethod == "POST")
        let body = try #require(request.httpBody)
        let sent = try JSONSerialization.jsonObject(with: body) as? NSDictionary
        #expect(sent == ["name": "Aluguel", "transactionType": "EXPENSE", "recurrenceDay": 5])
    }

    @Test("a series the mapper rejects is a server failure")
    func createUnmappable() async {
        let http = FakeHTTP(FakeHTTP.json(WireFixtures.series(type: "TRANSFER")))

        await #expect(throws: WriteFailure.server) { try await repository(http).create(draft) }
    }

    @Test("linking posts to the series' transaction path, with no body")
    func link() async throws {
        let http = FakeHTTP(FakeHTTP.empty(200))

        try await repository(http).link(transaction, to: series)

        #expect(http.requests.map(\.httpMethod) == ["POST"])
        #expect(http.requests.compactMap { $0.url?.absoluteString } == [
            "https://api.test/api/v1/recurring-series/s1/transactions/t1",
        ])
        #expect(http.requests.first?.httpBody == nil)
    }

    @Test("unlinking deletes the series' transaction path")
    func unlink() async throws {
        let http = FakeHTTP(FakeHTTP.empty(200))

        try await repository(http).unlink(transaction, from: series)

        #expect(http.requests.map(\.httpMethod) == ["DELETE"])
        #expect(http.requests.compactMap { $0.url?.path } == ["/api/v1/recurring-series/s1/transactions/t1"])
    }

    @Test("a 404 is vanished on every verb")
    func vanished() async {
        let repository = repository(FakeHTTP(FakeHTTP.empty(404)))

        await #expect(throws: WriteFailure.vanished) { try await repository.create(draft) }
        await #expect(throws: WriteFailure.vanished) { try await repository.link(transaction, to: series) }
        await #expect(throws: WriteFailure.vanished) { try await repository.unlink(transaction, from: series) }
    }
}
