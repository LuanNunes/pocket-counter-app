struct SessionState: Equatable, Sendable {
    /// Failed resolves in a row — the first attempt and one retry — before the escape is offered.
    static let escapeThreshold = 2

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
    /// Session fact, not view state: a `restore()` in flight must see it and drop its answer.
    var prefersPassword = false

    /// Whether `.undetermined` may reveal the way to Login.
    var offersEscape: Bool { failedResolveAttempts >= Self.escapeThreshold }
}
