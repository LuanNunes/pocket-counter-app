struct SessionState: Equatable, Sendable {
    /// Failed resolves in a row — the first attempt and two retries — before the escape to Login is offered.
    static let escapeThreshold = 3

    enum Gate: Equatable, Sendable {
        /// `restore()` has not answered yet.
        case resolving
        case signedIn(AuthenticatedUser)
        case signedOut
        /// The store could not be read. Never show Login here.
        case undetermined
    }

    var gate: Gate = .resolving
    /// A `restore()` is in flight; tells a retry apart from a gate that is merely `.undetermined`.
    var isResolving = false
    /// `signOut` threw: the tokens are still stored, so the gate did not move.
    var signOutFailed = false
    var failedResolveAttempts = 0

    /// Whether `.undetermined` may reveal its way to the Login screen.
    ///
    /// That escape is the one place Login is reachable from `.undetermined`. It must **never**
    /// call `signOut()` or touch `TokenStoring`: the stored session stays intact, and a fresh
    /// sign-in simply overwrites it.
    var offersEscape: Bool { failedResolveAttempts >= Self.escapeThreshold }
}
