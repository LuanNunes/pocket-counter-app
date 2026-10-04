actor TokenRefresher {
    enum Outcome: Sendable, Equatable {
        case refreshed(String)
        case sessionInvalid
        case unavailable
    }

    private struct RefreshBody: Encodable, Sendable { let token: String }
    private struct RefreshResponse: Decodable, Sendable {
        let accessToken: String
        let refreshToken: String
    }

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
        guard let current = await tokens.tokens() else { return .sessionInvalid }
        if let staleToken, current.accessToken != staleToken { return .refreshed(current.accessToken) }
        let endpoint = Endpoint<RefreshResponse>(
            method: .post, path: "api/v1/auth/refresh", body: RefreshBody(token: current.refreshToken)
        )
        do {
            let response = try await client.send(endpoint)
            let pair = TokenPair(accessToken: response.accessToken, refreshToken: response.refreshToken)
            try await tokens.save(pair)
            return .refreshed(pair.accessToken)
        } catch let error as APIError {
            guard case .status(let code, _) = error, code == 401 || code == 403 else { return .unavailable }
            await tokens.clear()
            return .sessionInvalid
        } catch {
            return .unavailable
        }
    }
}
