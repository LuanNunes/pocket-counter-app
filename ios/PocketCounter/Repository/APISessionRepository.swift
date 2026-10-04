import Foundation

/// Takes the raw client: every auth endpoint is unauthenticated.
struct APISessionRepository: SessionRepository {
    private struct LoginBody: Encodable, Sendable { let email: String; let password: String }
    private struct RegisterBody: Encodable, Sendable { let email: String; let name: String; let password: String }
    private struct TokenBody: Encodable, Sendable { let token: String }

    private let client: APIClient
    private let tokens: any TokenStoring

    init(client: APIClient, tokens: any TokenStoring) {
        self.client = client
        self.tokens = tokens
    }

    func restore() async -> SessionStatus {
        let stored: TokenPair?
        do {
            stored = try await tokens.tokens()
        } catch {
            return .undetermined
        }
        guard let stored else { return .signedOut }
        // `email` is not optional on `AuthenticatedUser`, and the token is the only source here —
        // `authenticate` can fall back to the typed credentials, this cannot. So a token missing
        // either the id or the email names nobody, and is discarded rather than kept: leaving it
        // stored would report `.signedOut` on every launch with no way to ever clear it.
        guard let user = JWTPayload(accessToken: stored.accessToken)?.user else {
            return await discardUnusable()
        }
        return .signedIn(user)
    }

    /// A token that names no user is no session, and `authenticate` would never have stored it.
    /// If it cannot be cleared, "signed out" would be a lie the next launch contradicts, so
    /// the honest answer is `.undetermined`.
    private func discardUnusable() async -> SessionStatus {
        do {
            try await tokens.clear()
        } catch {
            return .undetermined
        }
        return .signedOut
    }

    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) -> AuthenticatedUser {
        let endpoint = Endpoint<TokenResponse>(
            method: .post, path: "api/v1/auth/login", authentication: .credentials,
            body: LoginBody(email: credentials.email, password: credentials.password)
        )
        return try await authenticate(endpoint, name: nil, email: credentials.email)
    }

    func register(_ registration: Registration) async throws(AuthenticationFailure) -> AuthenticatedUser {
        let endpoint = Endpoint<TokenResponse>(
            method: .post, path: "api/v1/auth/register", authentication: .credentials,
            body: RegisterBody(email: registration.email, name: registration.name, password: registration.password)
        )
        return try await authenticate(endpoint, name: registration.name, email: registration.email)
    }

    /// The server call is best-effort; clearing the local session is not, and its failure is
    /// reported as `.server`: the vocabulary `authenticate` already uses for a storage failure.
    func signOut() async throws(AuthenticationFailure) {
        let refreshToken: String?
        do {
            refreshToken = try await tokens.tokens()?.refreshToken
        } catch {
            // Without a readable refresh token there is nothing to revoke server-side; the
            // local clear below still runs. `try?` here would yield `TokenPair??`.
            refreshToken = nil
        }
        if let refreshToken {
            let endpoint = Endpoint<EmptyResponse>(
                method: .post, path: "api/v1/auth/logout", authentication: .credentials, body: TokenBody(token: refreshToken)
            )
            try? await client.sendIgnoringResponse(endpoint)
        }
        do {
            try await tokens.clear()
        } catch {
            throw .server
        }
    }

    /// The identity comes from the token; `name` and `email` only fill in claims it omits.
    /// Nothing is stored before the token identifies its user — a session we cannot name is
    /// not a session.
    private func authenticate(
        _ endpoint: Endpoint<TokenResponse>,
        name: String?,
        email: String
    ) async throws(AuthenticationFailure) -> AuthenticatedUser {
        let response: TokenResponse
        do {
            response = try await client.send(endpoint)
        } catch {
            throw AuthenticationFailure(error)
        }
        guard let payload = JWTPayload(accessToken: response.accessToken), let userId = payload.userId else {
            throw .server
        }
        do {
            try await tokens.save(TokenPair(accessToken: response.accessToken, refreshToken: response.refreshToken))
        } catch {
            // Reported as `.server` though this is a local storage failure: a deliberate call,
            // keeping the sign-in error surface to what the backend vocabulary already covers.
            throw .server
        }
        return payload.user ?? AuthenticatedUser(id: userId, name: name, email: email)
    }
}

extension AuthenticationFailure {
    /// By HTTP status: the backend's `code` is too coarse to key on.
    init(_ error: APIError) {
        switch error {
        case .status(401, _):
            self = .invalidCredentials
        case .status(409, _):
            self = .emailAlreadyRegistered
        case .status(400, let server), .status(422, let server):
            self = .rejected(server?.message ?? "Não foi possível processar a solicitação")
        case .transport, .authenticationUnavailable:
            self = .unreachable
        case .cancelled:
            self = .abandoned
        case .sessionExpired, .status, .decoding, .invalidRequest:
            self = .server
        }
    }
}
