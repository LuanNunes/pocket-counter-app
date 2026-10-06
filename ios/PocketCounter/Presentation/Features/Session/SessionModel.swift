import Observation

@MainActor
@Observable
final class SessionModel {
    private(set) var state = SessionState()
    private let repository: any SessionRepository

    init(repository: any SessionRepository) {
        self.repository = repository
    }

    func resolve() async {
        // Two triggers: the root `.task` and the retry button.
        guard !state.isResolving else { return }
        state.isResolving = true
        defer { state.isResolving = false }

        switch await repository.restore() {
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

    /// Stores the tokens and moves the gate as one step; the form only renders the failure.
    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) {
        let user = try await repository.signIn(credentials)
        state.gate = .signedIn(user)
        state.failedResolveAttempts = 0
    }

    /// A first registration signs the user in, so it moves the gate exactly as `signIn` does.
    func register(_ registration: Registration) async throws(AuthenticationFailure) {
        let user = try await repository.register(registration)
        state.gate = .signedIn(user)
        state.failedResolveAttempts = 0
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
        sessionEnded()
    }

    func sessionEnded() {
        guard case .signedIn = state.gate else { return }
        state.gate = .signedOut
    }
}
