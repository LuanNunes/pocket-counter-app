import Foundation

/// Build-time configuration, injected into `Info.plist` from `Config/*.xcconfig`.
///
/// This is the iOS counterpart of Android's product flavors: one xcconfig per environment,
/// selected by the scheme.
enum AppEnvironment {

    /// Which environment this build points at, derived from the bundle id suffix.
    enum Name: String {
        case local, dev, prod
    }

    /// The backend root.
    ///
    /// Fails fast on purpose. A silently empty or malformed base URL turns every request
    /// into an opaque failure, which is hours of debugging for a one-line configuration
    /// mistake.
    static let baseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
              !raw.isEmpty
        else {
            fatalError("API_BASE_URL is missing from Info.plist — check Config/*.xcconfig")
        }

        guard let url = URL(string: raw), url.scheme != nil, url.host != nil else {
            // The usual cause: a bare `https://` in an xcconfig, where `//` starts a
            // comment and truncates the value to `https:`. Use $(SLASH).
            fatalError("API_BASE_URL is malformed: '\(raw)' — did you forget $(SLASH)?")
        }

        return url
    }()

    static let name: Name = {
        let bundleID = Bundle.main.bundleIdentifier ?? ""

        if bundleID.hasSuffix(".local") { return .local }
        if bundleID.hasSuffix(".dev") { return .dev }

        return .prod
    }()
}
