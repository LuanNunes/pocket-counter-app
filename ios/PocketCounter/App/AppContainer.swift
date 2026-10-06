import Foundation

/// The composition root.
///
/// Exactly one must exist: a second `TokenRefresher` would not coalesce with the first, and the
/// duplicate refresh 401s and clears the session. `init` is private for that reason.
@MainActor
struct AppContainer {
    private let tokens: KeychainTokenStore
    private let client: APIClient

    /// For repositories that call bearer-authenticated endpoints.
    let authenticatedClient: AuthenticatedAPIClient

    // Stored, never computed: the lookup repositories own a cache, and a fresh instance per access would have none.
    let transactionRepository: any TransactionRepository
    let tagRepository: any TagRepository
    let creditCardRepository: any CreditCardRepository

    static func make(configuration: AppConfiguration) -> AppContainer {
        AppContainer(configuration: configuration, keychain: .live, send: URLSessionHTTPSend.live())
    }

    /// Builds an isolated container over fakes. Production code uses `make(configuration:)`.
    static func forTesting(
        configuration: AppConfiguration,
        keychain: KeychainAccess,
        send: @escaping HTTPSend
    ) -> AppContainer {
        AppContainer(configuration: configuration, keychain: keychain, send: send)
    }

    private init(
        configuration: AppConfiguration,
        keychain: KeychainAccess,
        send: @escaping HTTPSend
    ) {
        let tokens = KeychainTokenStore(scope: configuration.environment.rawValue, access: keychain)
        let client = APIClient(baseURL: configuration.baseURL, send: send)
        let refresher = TokenRefresher(client: client, tokens: tokens)
        self.tokens = tokens
        self.client = client
        let authenticated = AuthenticatedAPIClient(client: client, tokens: tokens, refresher: refresher)
        self.authenticatedClient = authenticated
        transactionRepository = APITransactionRepository(client: authenticated)
        tagRepository = APITagRepository(client: authenticated)
        creditCardRepository = APICreditCardRepository(client: authenticated)
    }

    var loadLedger: LoadLedger {
        LoadLedger(transactions: transactionRepository, tags: tagRepository, cards: creditCardRepository)
    }

    /// Hands the session model the verb it needs, not the repositories.
    var endSession: SessionEndAction {
        let caches: [any LookupCaching] = [tagRepository, creditCardRepository]
        return { for cache in caches { await cache.invalidateLookups() } }
    }

    var sessionRepository: any SessionRepository { APISessionRepository(client: client, tokens: tokens) }
}
