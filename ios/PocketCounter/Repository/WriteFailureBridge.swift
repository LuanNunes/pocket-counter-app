import Foundation

extension AuthenticatedAPIClient {
    func write(_ endpoint: Endpoint<EmptyResponse>) async throws(WriteFailure) {
        do {
            try await sendIgnoringResponse(endpoint)
        } catch {
            throw WriteFailure(error)
        }
    }

    func write<R: Decodable & Sendable>(_ endpoint: Endpoint<R>) async throws(WriteFailure) -> R {
        do {
            return try await send(endpoint)
        } catch {
            throw WriteFailure(error)
        }
    }
}

extension WriteFailure {
    static func mapping<Input, Output>(
        _ input: Input, with transform: (Input) throws(MappingFailure) -> Output
    ) throws(WriteFailure) -> Output {
        do {
            return try transform(input)
        } catch {
            throw WriteFailure(error)
        }
    }
}
