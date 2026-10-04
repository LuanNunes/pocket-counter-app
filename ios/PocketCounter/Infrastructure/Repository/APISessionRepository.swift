import Foundation

/// Takes the raw client: every auth endpoint is unauthenticated.
struct APISessionRepository: SessionRepository {
    private struct LoginBody: Encodable, Sendable { let email: String; let password: String }
    private struct RegisterBody: Encodable, Sendable { let email: String; let name: String; let password: String }
    private struct TokenBody: Encodable, Sendable { let token: String }
    private struct TokenResponse: Decodable, Sendable {
        let accessToken: String
        let refreshToken: String
    }

    private let client: APIClient
    private let tokens: any TokenStoring

    init(client: APIClient, tokens: any TokenStoring) {
        self.client = client
        self.tokens = tokens
    }

    func restore() async -> SessionStatus {
        guard let user = await tokens.tokens().flatMap({ JWTPayload(accessToken: $0.accessToken)?.user }) else {
            return .signedOut
        }
        return .signedIn(user)
    }

    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) -> AuthenticatedUser {
        let endpoint = Endpoint<TokenResponse>(
            method: .post, path: "api/v1/auth/login",
            body: LoginBody(email: credentials.email, password: credentials.password)
        )
        return try await authenticate(endpoint, fallback: AuthenticatedUser(name: nil, email: credentials.email))
    }

    func register(_ registration: Registration) async throws(AuthenticationFailure) -> AuthenticatedUser {
        let endpoint = Endpoint<TokenResponse>(
            method: .post, path: "api/v1/auth/register",
            body: RegisterBody(email: registration.email, name: registration.name, password: registration.password)
        )
        return try await authenticate(
            endpoint, fallback: AuthenticatedUser(name: registration.name, email: registration.email)
        )
    }

    /// The server call is best-effort; the local session always ends.
    func signOut() async {
        if let refreshToken = await tokens.tokens()?.refreshToken {
            let endpoint = Endpoint<EmptyResponse>(
                method: .post, path: "api/v1/auth/logout", body: TokenBody(token: refreshToken)
            )
            try? await client.sendIgnoringResponse(endpoint)
        }
        await tokens.clear()
    }

    private func authenticate(
        _ endpoint: Endpoint<TokenResponse>,
        fallback: AuthenticatedUser
    ) async throws(AuthenticationFailure) -> AuthenticatedUser {
        let response: TokenResponse
        do {
            response = try await client.send(endpoint)
        } catch {
            throw AuthenticationFailure(error)
        }
        do {
            try await tokens.save(TokenPair(accessToken: response.accessToken, refreshToken: response.refreshToken))
        } catch {
            throw .server
        }
        return JWTPayload(accessToken: response.accessToken)?.user ?? fallback
    }
}

private extension AuthenticationFailure {
    /// By HTTP status: the backend's `code` is too coarse to key on.
    init(_ error: APIError) {
        switch error {
        case .status(401, _):
            self = .invalidCredentials
        case .status(409, _):
            self = .emailAlreadyRegistered
        case .status(400, let server), .status(422, let server):
            self = .rejected(server?.message ?? "Não foi possível processar a solicitação")
        case .transport, .cancelled:
            self = .unreachable
        case .status, .decoding, .invalidRequest:
            self = .server
        }
    }
}
