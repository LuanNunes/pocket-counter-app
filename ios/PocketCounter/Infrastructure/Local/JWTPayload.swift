import Foundation

/// Claims read from an access token for display only. The signature is not verified, and
/// must not be: the server is the authority on whether the token is valid.
struct JWTPayload: Decodable, Sendable, Equatable {
    let sub: String?
    let email: String?
    let name: String?

    init?(accessToken: String) {
        let segments = accessToken.split(separator: ".", omittingEmptySubsequences: false)
        guard segments.count == 3, let data = Self.decodeBase64URL(String(segments[1])),
              let payload = try? JSONDecoder().decode(JWTPayload.self, from: data)
        else { return nil }
        self = payload
    }

    var user: AuthenticatedUser? {
        email.map { AuthenticatedUser(name: name, email: $0) }
    }

    // base64url has no padding and a different alphabet; `Data(base64Encoded:)` accepts neither.
    private static func decodeBase64URL(_ segment: String) -> Data? {
        let standard = segment
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let padding = String(repeating: "=", count: (4 - standard.count % 4) % 4)
        return Data(base64Encoded: standard + padding)
    }
}
