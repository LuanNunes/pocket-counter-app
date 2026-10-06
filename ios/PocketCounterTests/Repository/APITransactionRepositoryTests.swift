import Foundation
import Testing

@testable import PocketCounter

@Suite("APITransactionRepository routes")
struct APITransactionRepositoryRouteTests {
    private let ref = RefYearMonth(raw: 202610)!

    @Test("each route names the backend path, with no leading slash, bearer-authenticated")
    func paths() throws {
        let span = try RefYearMonthRange(from: #require(RefYearMonth(raw: 202601)), through: #require(RefYearMonth(raw: 202612)))
        let routes: [(path: String, authentication: Endpoint<[TransactionDTO]>.Authentication)] = [
            (APITransactionRepository.Route.incomes(ref).path, APITransactionRepository.Route.incomes(ref).authentication),
            (APITransactionRepository.Route.expenses(ref).path, APITransactionRepository.Route.expenses(ref).authentication),
            (APITransactionRepository.Route.range(span).path, APITransactionRepository.Route.range(span).authentication),
        ]

        #expect(routes.map(\.path) == [
            "api/v1/transactions/incomes/202610",
            "api/v1/transactions/expenses/202610",
            "api/v1/transactions/range/202601/202612",
        ])
        #expect(routes.allSatisfy { $0.authentication == .bearer })
        #expect(APITransactionRepository.Route.items(TransactionID(rawValue: "t1")).path == "api/v1/transactions/t1/items")
    }
}

@Suite("APITransactionRepository")
struct APITransactionRepositoryTests {
    private let ref = RefYearMonth(raw: 202610)!

    private func list(_ rows: String...) -> FakeHTTP.Reply {
        FakeHTTP.json("[" + rows.joined(separator: ",") + "]")
    }

    private func repository(_ http: FakeHTTP) -> APITransactionRepository {
        APITransactionRepository(client: AuthenticatedFixture.client(http.send))
    }

    @Test("a month issues the income and expense requests and merges them into the contract order")
    func monthMerges() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/incomes/202610": list(
                WireFixtures.transaction(id: "b", type: "INCOME", displayOrder: 1, dateDue: "2026-10-02"),
                WireFixtures.transaction(id: "c", type: "INCOME", dateDue: "2026-10-09"),
                WireFixtures.transaction(id: "a", type: "INCOME", dateDue: "2026-10-09")
            ),
            "/api/v1/transactions/expenses/202610": list(
                WireFixtures.transaction(id: "x", type: "EXPENSE", dateDue: "2026-10-01")
            ),
        ])

        let items = try await repository(http).month(ref)

        #expect(items.map(\.id.rawValue) == ["a", "c", "b", "x"])
        #expect(Set(http.requests.compactMap { $0.url?.path }) == [
            "/api/v1/transactions/incomes/202610", "/api/v1/transactions/expenses/202610",
        ])
    }

    @Test("a range sorts by the wire's refYearMonth, not the row's date")
    func rangeSortsByRef() async throws {
        let span = try RefYearMonthRange(from: #require(RefYearMonth(raw: 202610)), through: #require(RefYearMonth(raw: 202611)))
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/range/202610/202611": list(
                WireFixtures.transaction(id: "later", type: "INCOME", ref: 202611, dateDue: "2026-10-30"),
                WireFixtures.transaction(id: "expense", type: "EXPENSE", ref: 202610, dateDue: "2026-10-01"),
                WireFixtures.transaction(id: "income", type: "INCOME", ref: 202610, dateDue: "2026-10-31")
            ),
        ])

        let items = try await repository(http).range(span)

        #expect(items.map(\.id.rawValue) == ["income", "expense", "later"])
        #expect(http.callCount == 1)
    }

    @Test("rows tied on every key but id come out the same whatever order the wire sent", arguments: [
        [0, 1, 2, 3], [3, 2, 1, 0], [2, 0, 3, 1], [1, 3, 0, 2],
    ])
    func tiesAreStable(permutation: [Int]) async throws {
        let ids = ["d", "a", "c", "b"]
        let rows = permutation.map { WireFixtures.transaction(id: ids[$0], type: "EXPENSE", dateDue: "2026-10-05") }
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/incomes/202610": list(),
            "/api/v1/transactions/expenses/202610": FakeHTTP.json("[" + rows.joined(separator: ",") + "]"),
        ])

        let items = try await repository(http).month(ref)

        #expect(items.map(\.id.rawValue) == ["a", "b", "c", "d"])
    }

    @Test("the income and expense requests are in flight together")
    func monthIsConcurrent() async throws {
        let http = FakeHTTP(FakeHTTP.json("[]"))
        let started = Counter()
        let gate = Gate()
        let send: HTTPSend = { request in
            started.increment()
            await gate.wait()
            return try await http.send(request)
        }
        let repository = APITransactionRepository(client: AuthenticatedFixture.client(send))

        let month = Task { try await repository.month(ref) }
        for _ in 0..<10_000 where started.count < 2 { await Task.yield() }
        let startedBeforeAnyReply = started.count
        await gate.open()
        _ = try await month.value

        #expect(startedBeforeAnyReply == 2)
    }

    @Test("a 401 is a lost session")
    func unauthorized() async {
        let http = FakeHTTP(FakeHTTP.empty(401))

        await #expect(throws: LoadFailure.sessionExpired) { try await repository(http).range(try span) }
    }

    @Test("a 400 is rejected with the server's first detail")
    func rejected() async {
        let http = FakeHTTP(FakeHTTP.json(#"{"message":"m","details":["período inválido"]}"#, status: 400))

        await #expect(throws: LoadFailure.rejected("período inválido")) { try await repository(http).range(try span) }
    }

    @Test("a cancelled request is abandoned")
    func cancelled() async {
        let http = FakeHTTP(.failure(URLError(.cancelled)))

        await #expect(throws: LoadFailure.abandoned) { try await repository(http).month(ref) }
    }

    @Test("a malformed row fails the load rather than being dropped")
    func malformedRow() async {
        let http = FakeHTTP(FakeHTTP.json("[" + WireFixtures.transaction(type: "TRANSFER") + "]"))

        await #expect(throws: LoadFailure.server) { try await repository(http).range(try span) }
    }

    @Test("invoice items come from the invoice's items path")
    func invoiceItems() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/transactions/t9/items": list(WireFixtures.item(id: "i1", idTransaction: "t9", amount: "20.00")),
        ])

        let items = try await repository(http).invoiceItems(TransactionID(rawValue: "t9"))

        #expect(items.map(\.id.rawValue) == ["i1"])
        #expect(items.first?.amount == Money(-20))
    }

    @Test("an unknown invoice is not found")
    func invoiceNotFound() async {
        let http = FakeHTTP(FakeHTTP.empty(404))

        await #expect(throws: LoadFailure.notFound) { try await repository(http).invoiceItems(TransactionID(rawValue: "x")) }
    }

    private var span: RefYearMonthRange {
        get throws { try RefYearMonthRange(from: #require(RefYearMonth(raw: 202610)), through: #require(RefYearMonth(raw: 202611))) }
    }
}
