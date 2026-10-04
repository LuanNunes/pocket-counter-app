enum SessionStatus: Sendable, Equatable {
    case signedOut
    case signedIn(AuthenticatedUser)
}
