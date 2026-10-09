import Foundation
import OSLog

extension ReadingFailure {
    private static let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "reading")

    /// `.cancelled` is unreachable: nothing cancels a sentence once it is sent.
    init(_ error: APIError) {
        switch error {
        case .sessionExpired, .status(401, _):
            self = .sessionExpired
        case .authenticationUnavailable:
            self = .authenticationUnavailable
        case .transport, .cancelled:
            self = .unreachable
        case .status(429, _):
            self = .tooManyRequests
        case .status(403, let server):
            self = .rejected(RejectionText.forbidden(server))
        case .status(400, let server), .status(422, let server):
            self = .rejected(RejectionText.unprocessable(server))
        case .decoding(let endpoint, _):
            Self.logger.error("Decoding failed for \(endpoint, privacy: .public)")
            self = .server
        case .status, .invalidRequest:
            self = .server
        }
    }

    /// Never logs the failure's payload: it can hold user text.
    init(_ failure: MappingFailure) {
        Self.logger.error("Mapping a sentence reading failed")
        self = .server
    }
}
