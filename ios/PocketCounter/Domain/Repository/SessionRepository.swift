enum AuthenticationFailure: Error, Equatable, Sendable {
    case invalidCredentials
    case emailAlreadyRegistered
    /// The server's own message, safe to show.
    case rejected(String)
    case unreachable
    case server
}

protocol SessionRepository: Sendable {
    func restore() async -> SessionStatus
    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) -> AuthenticatedUser
    func register(_ registration: Registration) async throws(AuthenticationFailure) -> AuthenticatedUser
    func signOut() async
}
