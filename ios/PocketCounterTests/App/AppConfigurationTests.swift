import Foundation
import Testing

@testable import PocketCounter

@Suite("AppConfiguration")
struct AppConfigurationTests {

    @Test("a known environment and a valid URL build a configuration")
    func valid() throws {
        let config = try AppConfiguration(environmentName: "dev", baseURLString: "https://api-dev.pocket-counter.com/")

        #expect(config.environment == .dev)
        #expect(config.baseURL.absoluteString == "https://api-dev.pocket-counter.com/")
    }

    @Test("every environment name parses", arguments: [("local", AppConfiguration.Environment.local), ("dev", .dev), ("prod", .prod)])
    func names(name: String, expected: AppConfiguration.Environment) throws {
        let config = try AppConfiguration(environmentName: name, baseURLString: "https://x.com/")
        #expect(config.environment == expected)
    }

    @Test("a missing or unknown environment is rejected, never defaulted to prod", arguments: [nil, "", "staging", "PROD"])
    func unknownEnvironment(name: String?) {
        #expect(throws: AppConfiguration.Invalid.unknownEnvironment(name)) {
            try AppConfiguration(environmentName: name, baseURLString: "https://x.com/")
        }
    }

    @Test("a missing or empty base URL is rejected", arguments: [nil, ""])
    func missingBaseURL(raw: String?) {
        #expect(throws: AppConfiguration.Invalid.missingBaseURL) {
            try AppConfiguration(environmentName: "dev", baseURLString: raw)
        }
    }

    @Test("the truncated URL left by an unescaped xcconfig double slash is rejected")
    func truncatedURL() {
        #expect(throws: AppConfiguration.Invalid.malformedBaseURL("https:")) {
            try AppConfiguration(environmentName: "dev", baseURLString: "https:")
        }
    }

    @Test("a base URL without the trailing slash `Endpoint` relies on gets one", arguments: [
        ("https://api.pocket-counter.com", "https://api.pocket-counter.com/"),
        ("https://api.pocket-counter.com/api", "https://api.pocket-counter.com/api/"),
        ("http://localhost:8080", "http://localhost:8080/"),
    ])
    func trailingSlash(raw: String, expected: String) throws {
        let config = try AppConfiguration(environmentName: "dev", baseURLString: raw)

        #expect(config.baseURL.absoluteString == expected)
    }

    @Test("a normalized base URL joins an endpoint path without swallowing the host")
    func joinsEndpointPath() throws {
        let config = try AppConfiguration(environmentName: "dev", baseURLString: "https://api.pocket-counter.com")
        let endpoint = Endpoint<EmptyResponse>(method: .post, path: "api/v1/auth/login", authentication: .credentials)

        let request = try endpoint.urlRequest(baseURL: config.baseURL)

        #expect(request.url?.absoluteString == "https://api.pocket-counter.com/api/v1/auth/login")
    }

    @Test("the main bundle reads both keys from its Info.plist")
    func mainBundle() throws {
        let config = try AppConfiguration.mainBundle()
        #expect(config.baseURL.host != nil)
        #expect(config.baseURL.absoluteString.hasSuffix("/"))
    }
}
