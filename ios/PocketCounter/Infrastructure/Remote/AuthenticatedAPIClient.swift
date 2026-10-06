import OSLog

struct AuthenticatedAPIClient: Sendable {
    private let client: APIClient
    private let tokens: any TokenStoring
    private let refresher: TokenRefresher
    private let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "auth")

    init(client: APIClient, tokens: any TokenStoring, refresher: TokenRefresher) {
        self.client = client
        self.tokens = tokens
        self.refresher = refresher
    }

    func send<R>(_ endpoint: Endpoint<R>) async throws(APIError) -> R {
        try await authorized(endpoint) { (authorized: Endpoint<R>) async throws(APIError) -> R in
            try await client.send(authorized)
        }
    }

    func sendIgnoringResponse(_ endpoint: Endpoint<EmptyResponse>) async throws(APIError) {
        try await authorized(endpoint) { (authorized: Endpoint<EmptyResponse>) async throws(APIError) in
            try await client.sendIgnoringResponse(authorized)
        }
    }

    /// The retry calls the raw client, never `self.send`: no recursion, so no loop.
    private func authorized<R, T>(
        _ endpoint: Endpoint<R>,
        call: (Endpoint<R>) async throws(APIError) -> T
    ) async throws(APIError) -> T {
        let sentToken: String?
        do {
            sentToken = try await tokens.tokens()?.accessToken
        } catch {
            throw .authenticationUnavailable
        }
        // A bearer endpoint with no token is not sent: it could succeed as an anonymous read.
        if endpoint.authentication == .bearer, sentToken == nil { throw .sessionExpired }
        do {
            return try await call(sentToken.map(endpoint.bearing) ?? endpoint)
        } catch {
            guard case .status(let code, _) = error, code == 401 else { throw error }
            guard endpoint.authentication == .bearer else { throw error }
            switch await refresher.accessToken(replacing: sentToken) {
            case .refreshed(let fresh):
                return try await retry(endpoint.bearing(fresh), call: call)
            case .sessionInvalid:
                throw .sessionExpired
            case .unavailable:
                throw .authenticationUnavailable
            }
        }
    }

    /// A server refusing a token it just minted has ended the session.
    private func retry<R, T>(
        _ endpoint: Endpoint<R>,
        call: (Endpoint<R>) async throws(APIError) -> T
    ) async throws(APIError) -> T {
        do {
            return try await call(endpoint)
        } catch {
            guard case .status(401, _) = error else { throw error }
            do {
                try await tokens.clear()
            } catch {
                logger.fault("Refused tokens could not be cleared; the next launch will act on them")
            }
            throw .sessionExpired
        }
    }
}
