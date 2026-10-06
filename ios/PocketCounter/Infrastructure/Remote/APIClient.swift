import Foundation

typealias HTTPSend = @Sendable (URLRequest) async throws -> (Data, HTTPURLResponse)

struct APIClient: Sendable {
    private let baseURL: URL
    private let transport: HTTPSend

    init(baseURL: URL, send: @escaping HTTPSend) {
        self.baseURL = baseURL
        self.transport = send
    }

    func send<R>(_ endpoint: Endpoint<R>) async throws(APIError) -> R {
        let data = try await perform(endpoint)
        do {
            return try JSONDecoder().decode(R.self, from: data)
        } catch {
            throw .decoding(endpoint: endpoint.label, underlying: String(describing: error))
        }
    }

    /// For endpoints that answer with no body, such as 204: decoding an empty body would throw.
    func sendIgnoringResponse(_ endpoint: Endpoint<EmptyResponse>) async throws(APIError) {
        _ = try await perform(endpoint)
    }

    private func perform<R>(_ endpoint: Endpoint<R>) async throws(APIError) -> Data {
        let request = try endpoint.urlRequest(baseURL: baseURL)
        let data: Data
        let response: HTTPURLResponse
        do {
            (data, response) = try await transport(request)
        } catch let error as URLError where error.code == .cancelled {
            throw .cancelled
        } catch is CancellationError {
            throw .cancelled
        } catch let error as URLError {
            throw .transport(error)
        } catch {
            throw .transport(URLError(.unknown))
        }
        guard (200..<300).contains(response.statusCode) else {
            throw .status(code: response.statusCode, server: ServerMessage(responseBody: data))
        }
        return data
    }
}

enum URLSessionHTTPSend {
    /// `waitsForConnectivity` stays false: with true, an offline request hangs instead of failing.
    static func live(timeout: TimeInterval = 30) -> HTTPSend {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = timeout
        configuration.waitsForConnectivity = false
        let session = URLSession(configuration: configuration)
        return { request in
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            return (data, http)
        }
    }
}
