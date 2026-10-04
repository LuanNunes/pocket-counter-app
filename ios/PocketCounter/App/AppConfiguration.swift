import Foundation

/// Build-time configuration, injected into `Info.plist` from `Config/*.xcconfig`.
struct AppConfiguration: Sendable {
    enum Environment: String, Sendable { case local, dev, prod }

    enum Invalid: Error, Equatable {
        case missingBaseURL
        case malformedBaseURL(String)
        case unknownEnvironment(String?)
    }

    let environment: Environment
    let baseURL: URL

    init(environmentName: String?, baseURLString: String?) throws {
        guard let environment = environmentName.flatMap(Environment.init(rawValue:)) else {
            throw Invalid.unknownEnvironment(environmentName)
        }
        guard let raw = baseURLString, !raw.isEmpty else { throw Invalid.missingBaseURL }
        // A bare `https://` in an xcconfig is truncated to `https:`; $(SLASH) avoids it.
        guard let url = URL(string: raw), url.scheme != nil, url.host() != nil else {
            throw Invalid.malformedBaseURL(raw)
        }
        self.environment = environment
        self.baseURL = url
    }

    static func mainBundle(_ bundle: Bundle = .main) throws -> AppConfiguration {
        try AppConfiguration(
            environmentName: bundle.object(forInfoDictionaryKey: "APP_ENVIRONMENT") as? String,
            baseURLString: bundle.object(forInfoDictionaryKey: "API_BASE_URL") as? String
        )
    }
}
