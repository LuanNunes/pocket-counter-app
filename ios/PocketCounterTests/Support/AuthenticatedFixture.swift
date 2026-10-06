import Foundation

@testable import PocketCounter

enum AuthenticatedFixture {
    static let baseURL = URL(string: "https://api.test/") ?? URL(fileURLWithPath: "/")

    /// Holds a token, so a bearer request is sent.
    static func client(_ send: @escaping HTTPSend) -> AuthenticatedAPIClient {
        let tokens = InMemoryTokenStore(TokenPair(accessToken: "access", refreshToken: "refresh"))
        let raw = APIClient(baseURL: baseURL, send: send)
        return AuthenticatedAPIClient(client: raw, tokens: tokens, refresher: TokenRefresher(client: raw, tokens: tokens))
    }
}
