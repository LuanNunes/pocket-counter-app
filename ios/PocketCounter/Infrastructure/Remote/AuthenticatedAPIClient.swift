struct AuthenticatedAPIClient: Sendable {
    private let client: APIClient
    private let tokens: any TokenStoring
    private let refresher: TokenRefresher

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
        let sentToken = await tokens.tokens()?.accessToken
        do {
            return try await call(sentToken.map(endpoint.bearing) ?? endpoint)
        } catch {
            guard case .status(let code, _) = error, code == 401 else { throw error }
            // Paid bug on Android: a 401 from /auth/ (a dead refresh token) must not refresh again.
            guard !endpoint.path.hasPrefix("api/v1/auth/") else { throw error }
            guard case .refreshed(let fresh) = await refresher.accessToken(replacing: sentToken) else { throw error }
            return try await call(endpoint.bearing(fresh))
        }
    }
}
