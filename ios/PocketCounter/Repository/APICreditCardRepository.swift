import Foundation

struct APICreditCardRepository: CreditCardRepository {
    enum Route {
        static var cards: Endpoint<[CreditCardDTO]> {
            Endpoint(method: .get, path: "api/v1/credit-cards", authentication: .bearer)
        }
    }

    private let client: AuthenticatedAPIClient
    private let cache = CachedValue<[CreditCard]>()

    init(client: AuthenticatedAPIClient) {
        self.client = client
    }

    func cards() async throws(LoadFailure) -> [CreditCard] {
        let client = client
        return try await cache.value { () async throws(LoadFailure) -> [CreditCard] in
            let cards = try await client.load(Route.cards).mappedOrFailing(CreditCardMapper.map)
            return ReadingOrder.byName(cards, name: \.name, id: \.id.rawValue)
        }
    }

    func invalidateLookups() async {
        await cache.invalidate()
    }
}
