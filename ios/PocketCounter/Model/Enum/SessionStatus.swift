enum SessionStatus: Sendable, Equatable {
    case signedOut
    case signedIn(AuthenticatedUser)
    /// The stored session could not be read — not the same as not having one. The gate must
    /// offer a retry and must never show the login screen here.
    case undetermined
}
