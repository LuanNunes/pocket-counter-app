import Observation

/// What must be forgotten when a session ends, so the next account never reads this one's data.
typealias SessionEndAction = @Sendable () async -> Void

@MainActor
@Observable
final class SessionModel {
    private(set) var state = SessionState()
    private let repository: any SessionRepository
    private let onSessionEnd: SessionEndAction

    init(repository: any SessionRepository, onSessionEnd: @escaping SessionEndAction) {
        self.repository = repository
        self.onSessionEnd = onSessionEnd
    }

    func resolve() async {
        // Two triggers: the root `.task` and the retry button.
        guard !state.isResolving, !state.prefersPassword else { return }
        state.isResolving = true
        defer { state.isResolving = false }

        let status = await repository.restore()
        guard !state.prefersPassword else { return }
        switch status {
        case .signedIn(let user):
            state.gate = .signedIn(user)
            state.failedResolveAttempts = 0
        case .signedOut:
            state.gate = .signedOut
            state.failedResolveAttempts = 0
        case .undetermined:
            state.gate = .undetermined
            state.failedResolveAttempts += 1
        }
    }

    func preferPassword() {
        state.prefersPassword = true
    }

    /// Back from the escape Login to the splash. The gate and the failed attempts are untouched.
    func cancelPasswordEscape() {
        state.prefersPassword = false
    }

    /// Stores the tokens and moves the gate as one step; the form only renders the failure.
    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) {
        let user = try await repository.signIn(credentials)
        authenticated(as: user)
    }

    /// A first registration signs the user in, so it moves the gate exactly as `signIn` does.
    func register(_ registration: Registration) async throws(AuthenticationFailure) {
        let user = try await repository.register(registration)
        authenticated(as: user)
    }

    private func authenticated(as user: AuthenticatedUser) {
        state.gate = .signedIn(user)
        state.failedResolveAttempts = 0
        state.prefersPassword = false
    }

    /// On failure the tokens are still stored, so the gate stays `.signedIn`: showing Login
    /// for a session the next launch restores is the bug the repository contract prevents.
    func signOut() async {
        state.signOutFailed = false
        do {
            try await repository.signOut()
        } catch {
            switch error {
            case .abandoned:
                return
            case .invalidCredentials, .emailAlreadyRegistered, .rejected, .unreachable, .server:
                state.signOutFailed = true
                return
            }
        }
        await sessionEnded()
    }

    func sessionEnded() async {
        guard case .signedIn = state.gate else { return }
        state.gate = .signedOut
        await onSessionEnd()
    }
}
