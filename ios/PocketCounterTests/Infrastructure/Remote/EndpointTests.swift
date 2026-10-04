import Foundation
import Testing

@testable import PocketCounter

@Suite("Endpoint")
struct EndpointTests {

    private struct Body: Encodable, Sendable { let email: String }

    @Test("the path is appended to the base path", arguments: [
        ("http://localhost:8080/", "http://localhost:8080/api/v1/auth/login"),
        ("https://api-dev.pocket-counter.com/", "https://api-dev.pocket-counter.com/api/v1/auth/login"),
        ("https://host.com/prefix/", "https://host.com/prefix/api/v1/auth/login"),
    ])
    func urlAssembly(base: String, expected: String) throws {
        let endpoint = Endpoint<EmptyResponse>(method: .post, path: "api/v1/auth/login")

        let request = try endpoint.urlRequest(baseURL: #require(URL(string: base)))

        #expect(request.url?.absoluteString == expected)
    }

    @Test("a leading slash on the path is rejected, since it would drop the base path")
    func leadingSlash() throws {
        let endpoint = Endpoint<EmptyResponse>(method: .get, path: "/api/v1/x")
        let base = try #require(URL(string: "https://host.com/prefix/"))

        #expect(throws: APIError.self) { try endpoint.urlRequest(baseURL: base) }
    }

    @Test("query items are encoded onto the URL")
    func query() throws {
        let endpoint = Endpoint<EmptyResponse>(
            method: .get, path: "api/v1/t", query: [URLQueryItem(name: "ref", value: "2026-10")]
        )

        let request = try endpoint.urlRequest(baseURL: #require(URL(string: "https://h.com/")))

        #expect(request.url?.absoluteString == "https://h.com/api/v1/t?ref=2026-10")
    }

    @Test("a request without a body sends Accept only")
    func noBodyHeaders() throws {
        let endpoint = Endpoint<EmptyResponse>(method: .get, path: "api/v1/x")

        let request = try endpoint.urlRequest(baseURL: #require(URL(string: "https://h.com/")))

        #expect(request.httpMethod == "GET")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == nil)
        #expect(request.httpBody == nil)
    }

    @Test("a request with a body sends it as JSON")
    func bodyHeaders() throws {
        let endpoint = Endpoint<EmptyResponse>(method: .post, path: "api/v1/x", body: Body(email: "a@b.co"))

        let request = try endpoint.urlRequest(baseURL: #require(URL(string: "https://h.com/")))

        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/json")
        #expect(request.value(forHTTPHeaderField: "Accept") == "application/json")
        #expect(request.httpBody.flatMap { String(data: $0, encoding: .utf8) } == #"{"email":"a@b.co"}"#)
    }
}
