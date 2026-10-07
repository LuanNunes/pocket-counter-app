import Foundation

extension AuthenticatedAPIClient {
    func write(_ endpoint: Endpoint<EmptyResponse>) async throws(WriteFailure) {
        do {
            try await sendIgnoringResponse(endpoint)
        } catch {
            throw WriteFailure(error)
        }
    }
}
