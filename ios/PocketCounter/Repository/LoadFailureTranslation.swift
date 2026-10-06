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
            self = .rejected(Self.text(server?.message) ?? "Você não tem acesso a este recurso")
        case .status(404, _):
            self = .notFound
        case .status(400, let server), .status(422, let server):
            self = .rejected(
                Self.text(server?.details.first) ?? Self.text(server?.message) ?? "Não foi possível processar a solicitação"
            )
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

    private static func text(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }
}
