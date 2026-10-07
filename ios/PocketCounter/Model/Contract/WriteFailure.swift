import Foundation

enum WriteFailure: Error, Equatable, Sendable {
    case sessionExpired
    case authenticationUnavailable
    case unreachable
    case vanished
    case rejected(String)
    case server
}
