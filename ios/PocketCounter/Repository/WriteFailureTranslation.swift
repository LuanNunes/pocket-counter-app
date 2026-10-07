import Foundation
import OSLog

extension WriteFailure {
    private static let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "writing")

    /// A write's 404 means the row is gone or never the user's, which a read's does not.
    /// `.cancelled` is unreachable: nothing cancels a write, and both status endpoints are idempotent.
    init(_ error: APIError) {
        switch error {
        case .sessionExpired, .status(401, _):
            self = .sessionExpired
        case .authenticationUnavailable:
            self = .authenticationUnavailable
        case .transport, .cancelled:
            self = .unreachable
        case .status(404, _):
            self = .vanished
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
}
