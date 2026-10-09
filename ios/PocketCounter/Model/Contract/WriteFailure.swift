import Foundation

enum WriteFailure: Error, Equatable, Sendable {
    case sessionExpired
    case authenticationUnavailable
    case unreachable
    case vanished
    case rejected(String)
    /// 409: the server named an existing row. The client may retry with `allowDuplicate`.
    case duplicate(String)
    case server
}
