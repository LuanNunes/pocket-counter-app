import Foundation

/// The composition root.
///
/// **Exactly one `KeychainTokenStore` must exist.** It memoizes what it read, and three types
/// take `any TokenStoring`; two instances would hold independent caches, so a session cleared
/// by the refresher would resurrect from the repository's stale one. Only building it here
/// prevents that — the main reason this type exists.
@MainActor
struct AppContainer {
    private let tokens: KeychainTokenStore
    private let client: APIClient
    private let refresher: TokenRefresher
    private let authenticatedClient: AuthenticatedAPIClient

    /// `keychain` and `send` are the test seams.
    init(
        configuration: AppConfiguration,
        keychain: KeychainAccess = .live,
        send: @escaping HTTPSend = URLSessionHTTPSend.live()
    ) {
        let tokens = KeychainTokenStore(scope: configuration.environment.rawValue, access: keychain)
        let client = APIClient(baseURL: configuration.baseURL, send: send)
        let refresher = TokenRefresher(client: client, tokens: tokens)
        self.tokens = tokens
        self.client = client
        self.refresher = refresher
        self.authenticatedClient = AuthenticatedAPIClient(client: client, tokens: tokens, refresher: refresher)
    }

    var sessionRepository: any SessionRepository { APISessionRepository(client: client, tokens: tokens) }
}
