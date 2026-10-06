@testable import PocketCounter

/// Scripted at `init`: `restores` are handed out in order (the last repeats), each `signOutFailures`
/// entry fails one sign-out (then it succeeds), and `hold()` makes
/// the next `restore()` suspend until `release()`, so a test can observe the in-flight state.
actor FakeSessionRepository: SessionRepository {
    private var restores: [SessionStatus]
    private let signInResult: Result<AuthenticatedUser, AuthenticationFailure>
    private var signOutFailures: [AuthenticationFailure]
    private var holding = false
    private var waiter: CheckedContinuation<Void, Never>?

    private(set) var restoreCount = 0
    private(set) var signInCount = 0
    private(set) var signOutCount = 0

    init(
        restores: [SessionStatus] = [.signedOut],
        signIn: Result<AuthenticatedUser, AuthenticationFailure> = .failure(.server),
        signOutFailures: [AuthenticationFailure] = []
    ) {
        self.restores = restores
        self.signInResult = signIn
        self.signOutFailures = signOutFailures
    }

    func hold() { holding = true }

    /// Returns once a `restore()` is suspended on the hold.
    func untilRestoreIsSuspended() async {
        while waiter == nil { await Task.yield() }
    }

    func release() {
        holding = false
        waiter?.resume()
        waiter = nil
    }

    func restore() async -> SessionStatus {
        restoreCount += 1
        if holding {
            await withCheckedContinuation { waiter = $0 }
        }
        return restores.count > 1 ? restores.removeFirst() : restores[0]
    }

    func signIn(_ credentials: LoginCredentials) async throws(AuthenticationFailure) -> AuthenticatedUser {
        signInCount += 1
        return try signInResult.get()
    }

    func register(_ registration: Registration) async throws(AuthenticationFailure) -> AuthenticatedUser {
        try signInResult.get()
    }

    func signOut() async throws(AuthenticationFailure) {
        signOutCount += 1
        guard !signOutFailures.isEmpty else { return }
        throw signOutFailures.removeFirst()
    }
}
