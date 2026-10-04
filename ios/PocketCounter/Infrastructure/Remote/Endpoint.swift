import Foundation

struct EmptyResponse: Decodable, Sendable {}

struct Endpoint<Response: Decodable & Sendable>: Sendable {
    enum Method: String, Sendable { case get = "GET", post = "POST", put = "PUT", patch = "PATCH", delete = "DELETE" }

    enum Authentication: Sendable {
        /// The stored access token. A 401 means that token is stale: refresh once and retry.
        case bearer
        /// A credential carried in the request itself (a password, a refresh token) or none at
        /// all. A 401 is the server rejecting *that* credential, so refreshing would both loop
        /// and sign out a user whose session is fine.
        case credentials
    }

    let method: Method
    /// Relative to the base URL, with no leading slash: a leading slash would replace the base path.
    let path: String
    let authentication: Authentication
    let query: [URLQueryItem]
    let body: (any Encodable & Sendable)?
    let headers: [String: String]

    init(
        method: Method,
        path: String,
        authentication: Authentication,
        query: [URLQueryItem] = [],
        body: (any Encodable & Sendable)? = nil,
        headers: [String: String] = [:]
    ) {
        self.method = method
        self.path = path
        self.authentication = authentication
        self.query = query
        self.body = body
        self.headers = headers
    }

    func bearing(_ token: String) -> Endpoint {
        Endpoint(
            method: method, path: path, authentication: authentication, query: query, body: body,
            headers: headers.merging(["Authorization": "Bearer \(token)"]) { _, new in new }
        )
    }

    var label: String { "\(method.rawValue) \(path)" }

    /// `baseURL` must end in `/`.
    func urlRequest(baseURL: URL) throws(APIError) -> URLRequest {
        guard !path.hasPrefix("/") else { throw .invalidRequest("path must not start with '/': \(path)") }
        guard var components = URLComponents(string: baseURL.absoluteString + path) else {
            throw .invalidRequest("malformed URL for \(label)")
        }
        components.queryItems = query.isEmpty ? nil : query
        guard let url = components.url else { throw .invalidRequest("malformed URL for \(label)") }

        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        headers.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        guard let body else { return request }

        do {
            request.httpBody = try JSONEncoder().encode(body)
        } catch {
            throw .invalidRequest("cannot encode body for \(label): \(error)")
        }
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        return request
    }
}
