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
        self.authenticatedClient = AuthenticatedAPIClient(client: client, tokens: tokens, refresher: refresher)
    }

    var sessionRepository: any SessionRepository { APISessionRepository(client: client, tokens: tokens) }
}
