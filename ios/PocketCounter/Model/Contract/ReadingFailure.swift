import Foundation

enum ReadingFailure: Error, Equatable, Sendable {
    case sessionExpired
    case authenticationUnavailable
    case unreachable
    /// 30 requests a minute: something the user can wait out, unlike `server`.
    case tooManyRequests
    case rejected(String)
    case server
}
