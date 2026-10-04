import OSLog

actor TokenRefresher {
    enum Outcome: Sendable, Equatable {
        case refreshed(String)
        case sessionInvalid
        case unavailable
    }

    private struct RefreshBody: Encodable, Sendable { let token: String }

    private let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "auth")
    private var inFlight: Task<Outcome, Never>?
    private let client: APIClient
    private let tokens: any TokenStoring

    init(client: APIClient, tokens: any TokenStoring) {
        self.client = client
        self.tokens = tokens
    }

    /// Single-flight: the backend answers a second refresh with the same token by 401, and a 401
    /// here clears the session, so a duplicate refresh would sign out a valid user.
    /// Actors are reentrant: no `await` may sit between reading and writing `inFlight`.
    func accessToken(replacing staleToken: String?) async -> Outcome {
        if let existing = inFlight { return await existing.value }
        let task = Task { await performRefresh(replacing: staleToken) }
        inFlight = task
        let outcome = await task.value
        if inFlight == task { inFlight = nil }
        return outcome
    }

    private func performRefresh(replacing staleToken: String?) async -> Outcome {
        let current: TokenPair?
        do { current = try await tokens.tokens() } catch { return .unavailable }
        guard let current else { return .sessionInvalid }
        if let staleToken, current.accessToken != staleToken { return .refreshed(current.accessToken) }
        let endpoint = Endpoint<TokenResponse>(
            method: .post, path: "api/v1/auth/refresh", authentication: .credentials,
            body: RefreshBody(token: current.refreshToken)
        )
        let response: TokenResponse
        do {
            response = try await client.send(endpoint)
        } catch {
            guard case .status(let code, _) = error, code == 401 || code == 403 else { return .unavailable }
            do {
                try await tokens.clear()
            } catch {
                logger.fault("Revoked tokens could not be cleared; the next launch will act on them")
            }
            return .sessionInvalid
        }
        let pair = TokenPair(accessToken: response.accessToken, refreshToken: response.refreshToken)
        do {
            try await tokens.save(pair)
        } catch {
            // The server already revoked the old refresh token; the new access token is good, so
            // let the in-flight request finish. Accepted: the store still holds the revoked pair,
            // so the next 401 signs the user out.
            logger.error("Rotated tokens could not be persisted; session will not survive relaunch")
        }
        return .refreshed(pair.accessToken)
    }
}
