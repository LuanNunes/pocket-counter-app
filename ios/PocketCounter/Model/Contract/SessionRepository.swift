enum AuthenticationFailure: Error, Equatable, Sendable {
    case invalidCredentials
    case emailAlreadyRegistered
    /// The server's own message, safe to show.
    case rejected(String)
    case unreachable
    case server
    /// The caller abandoned the request — the screen was left, the task was cancelled. The UI
    /// must render **nothing**: no banner, no alert, no retry. Nobody is waiting for an answer.
    case abandoned
}

protocol SessionRepository: Sendable {
    func restore() async -> SessionStatus
    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) -> AuthenticatedUser
    func register(_ registration: Registration) async throws(AuthenticationFailure) -> AuthenticatedUser
    /// `.server` when the local session could not be ended: the tokens are still stored, so the
    /// caller must not present the user as signed out.
    func signOut() async throws(AuthenticationFailure)
}
