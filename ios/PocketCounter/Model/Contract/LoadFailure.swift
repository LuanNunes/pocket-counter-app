import Foundation

enum LoadFailure: Error, Equatable, Sendable {
    case sessionExpired
    case authenticationUnavailable
    case unreachable
    case notFound
    case rejected(String)
    case server
    case abandoned
}
