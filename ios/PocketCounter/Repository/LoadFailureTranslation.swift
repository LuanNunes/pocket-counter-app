import Foundation
import OSLog

extension LoadFailure {
    private static let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "loading")

    init(_ error: APIError) {
        switch error {
        case .sessionExpired, .status(401, _):
            self = .sessionExpired
        case .authenticationUnavailable:
            self = .authenticationUnavailable
        case .cancelled:
            self = .abandoned
        case .transport:
            self = .unreachable
        case .status(403, let server):
            self = .rejected(RejectionText.forbidden(server))
        case .status(404, _):
            self = .notFound
        case .status(400, let server), .status(422, let server):
            self = .rejected(RejectionText.unprocessable(server))
        case .decoding(let endpoint, _):
            Self.logger.error("Decoding failed for \(endpoint, privacy: .public)")
            self = .server
        case .status, .invalidRequest:
            self = .server
        }
    }

    /// Never logs `value`: it can hold user text.
    init(_ failure: MappingFailure) {
        switch failure {
        case .missingField(let entity, let field), .unknownEnum(let entity, let field, _):
            Self.logger.error("Mapping failed: \(entity, privacy: .public).\(field, privacy: .public)")
        case .invalidDate(let entity, _):
            Self.logger.error("Mapping failed: \(entity, privacy: .public) has an invalid date")
        case .invalidRef(let ref):
            Self.logger.error("Mapping failed: invalid ref month \(ref, privacy: .public)")
        }
        self = .server
    }
}
