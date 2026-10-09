import Foundation

extension AuthenticatedAPIClient {
    func read<R: Decodable & Sendable>(_ endpoint: Endpoint<R>) async throws(ReadingFailure) -> R {
        do {
            return try await send(endpoint)
        } catch {
            throw ReadingFailure(error)
        }
    }
}
