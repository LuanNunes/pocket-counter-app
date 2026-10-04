import Foundation

enum APIError: Error, Sendable {
    case invalidRequest(String)
    case transport(URLError)
    case status(code: Int, server: ServerMessage?)
    case decoding(endpoint: String, underlying: String)
    case cancelled
}

struct ServerMessage: Sendable, Equatable {
    let code: String
    let message: String
    let details: [String]
    let correlationId: String?
}

extension ServerMessage {
    /// Nil when the body is not the backend's error envelope (an HTML 502, an empty body).
    init?(responseBody: Data) {
        struct Wire: Decodable {
            let code: String?
            let message: String?
            let details: [String]?
            let correlationId: String?
        }
        guard let wire = try? JSONDecoder().decode(Wire.self, from: responseBody),
              wire.code != nil || wire.message != nil
        else { return nil }
        self.init(
            code: wire.code ?? "",
            message: wire.message ?? "",
            details: wire.details ?? [],
            correlationId: wire.correlationId
        )
    }
}
