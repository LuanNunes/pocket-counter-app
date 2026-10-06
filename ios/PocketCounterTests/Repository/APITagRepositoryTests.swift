import Foundation
import Testing

@testable import PocketCounter

@Suite("APITagRepository")
struct APITagRepositoryTests {
    private func list(_ rows: String...) -> FakeHTTP.Reply {
        FakeHTTP.json("[" + rows.joined(separator: ",") + "]")
    }

    private func repository(_ http: FakeHTTP) -> APITagRepository {
        APITagRepository(client: AuthenticatedFixture.client(http.send))
    }

    @Test("each route names the backend path, bearer-authenticated")
    func paths() {
        #expect(APITagRepository.Route.tags.path == "api/v1/tags")
        #expect(APITagRepository.Route.tags(in: ContextID(rawValue: "c1")).path == "api/v1/tags/category/c1")
        #expect(APITagRepository.Route.categories.path == "api/v1/categories")
        #expect(APITagRepository.Route.tags.authentication == .bearer)
        #expect(APITagRepository.Route.categories.authentication == .bearer)
    }

    @Test("tags sort by name the way the user reads it, ties broken by id")
    func tagsSortedByName() async throws {
        let http = FakeHTTP(list(
            WireFixtures.tag(id: "3", name: "mercado"),
            WireFixtures.tag(id: "2", name: "Álcool"),
            WireFixtures.tag(id: "1", name: "mercado"),
            WireFixtures.tag(id: "4", name: "Zoo")
        ))

        let tags = try await repository(http).tags()

        #expect(tags.map(\.id.rawValue) == ["2", "1", "3", "4"])
    }

    @Test("categories sort by displayOrder with nulls last, then name, then id")
    func categoriesTotalOrder() async throws {
        let http = FakeHTTP(list(
            WireFixtures.category(id: "6", name: "Sem ordem", displayOrder: nil),
            WireFixtures.category(id: "5", name: "Beta", displayOrder: 2),
            WireFixtures.category(id: "4", name: "Alfa", displayOrder: 2),
            WireFixtures.category(id: "3", name: "Casa", displayOrder: 1),
            WireFixtures.category(id: "2", name: "Casa", displayOrder: 1),
            WireFixtures.category(id: "1", name: "Aaa", displayOrder: nil)
        ))

        let categories = try await repository(http).categories()

        #expect(categories.map(\.id.rawValue) == ["2", "3", "4", "5", "1", "6"])
    }

    @Test("tags of a category come from that category's path, sorted by name")
    func tagsInCategory() async throws {
        let http = FakeHTTP(routes: [
            "/api/v1/tags/category/c1": list(WireFixtures.tag(id: "2", name: "B"), WireFixtures.tag(id: "1", name: "A")),
        ])

        let tags = try await repository(http).tags(in: ContextID(rawValue: "c1"))

        #expect(tags.map(\.id.rawValue) == ["1", "2"])
    }

    @Test("a repeated read within the TTL is served from the cache until invalidated")
    func caches() async throws {
        let http = FakeHTTP(list(WireFixtures.tag()))
        let repository = repository(http)

        _ = try await repository.tags()
        _ = try await repository.tags()
        #expect(http.callCount == 1)

        await repository.invalidateLookups()
        _ = try await repository.tags()
        #expect(http.callCount == 2)
    }

    @Test("invalidation drops the categories too")
    func invalidatesCategories() async throws {
        let http = FakeHTTP(list(WireFixtures.category()))
        let repository = repository(http)

        _ = try await repository.categories()
        await repository.invalidateLookups()
        _ = try await repository.categories()

        #expect(http.callCount == 2)
    }

    @Test("a 401 is a lost session")
    func unauthorized() async {
        let http = FakeHTTP(FakeHTTP.empty(401))

        await #expect(throws: LoadFailure.sessionExpired) { try await repository(http).tags() }
    }

    @Test("a failed load is not cached")
    func failureIsNotCached() async throws {
        let http = FakeHTTP(.failure(URLError(.notConnectedToInternet)), list(WireFixtures.tag()))
        let repository = repository(http)

        await #expect(throws: LoadFailure.unreachable) { try await repository.tags() }
        let tags = try await repository.tags()

        #expect(tags.count == 1)
    }

    @Test("a row with an unknown tag kind fails the load")
    func unknownKind() async {
        let http = FakeHTTP(list(WireFixtures.tag(kind: "TRANSFER")))

        await #expect(throws: LoadFailure.server) { try await repository(http).tags() }
    }
}
