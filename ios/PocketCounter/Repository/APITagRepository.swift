import Foundation

struct APITagRepository: TagRepository {
    enum Route {
        static var tags: Endpoint<[TagDTO]> {
            Endpoint(method: .get, path: "api/v1/tags", authentication: .bearer)
        }

        static func tags(in category: ContextID) -> Endpoint<[TagDTO]> {
            Endpoint(method: .get, path: "api/v1/tags/category/\(category.rawValue)", authentication: .bearer)
        }

        static var categories: Endpoint<[CategoryDTO]> {
            Endpoint(method: .get, path: "api/v1/categories", authentication: .bearer)
        }
    }

    private let client: AuthenticatedAPIClient
    private let tagCache = CachedValue<[Tag]>()
    private let categoryCache = CachedValue<[TagContext]>()

    init(client: AuthenticatedAPIClient) {
        self.client = client
    }

    func tags() async throws(LoadFailure) -> [Tag] {
        let client = client
        return try await tagCache.value { () async throws(LoadFailure) -> [Tag] in try await Self.tags(Route.tags, client) }
    }

    func tags(in category: ContextID) async throws(LoadFailure) -> [Tag] {
        try await Self.tags(Route.tags(in: category), client)
    }

    func categories() async throws(LoadFailure) -> [TagContext] {
        let client = client
        return try await categoryCache.value { () async throws(LoadFailure) -> [TagContext] in
            let categories = try await client.load(Route.categories)
            return try Self.sortedCategories(categories).mappedOrFailing(TagMapper.context)
        }
    }

    func invalidateLookups() async {
        await tagCache.invalidate()
        await categoryCache.invalidate()
    }

    private static func tags(_ endpoint: Endpoint<[TagDTO]>, _ client: AuthenticatedAPIClient) async throws(LoadFailure) -> [Tag] {
        let tags = try await client.load(endpoint).mappedOrFailing(TagMapper.tag)
        return ReadingOrder.byName(tags, name: \.name, id: \.id.rawValue)
    }

    /// The server sorts on a nullable column with no tie-break: nulls go last, then name, then `id`.
    private static func sortedCategories(_ categories: [CategoryDTO]) -> [CategoryDTO] {
        categories.sorted { lhs, rhs in
            switch (lhs.displayOrder, rhs.displayOrder) {
            case (let left?, let right?) where left != right: return left < right
            case (nil, .some): return false
            case (.some, nil): return true
            case (.some, .some), (nil, nil): break
            }
            switch ReadingOrder.compare(lhs.name, rhs.name, locale: ReadingOrder.locale) {
            case .orderedAscending: return true
            case .orderedDescending: return false
            case .orderedSame: return lhs.id < rhs.id
            }
        }
    }
}
