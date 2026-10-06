import Foundation

@testable import PocketCounter

enum JWTFixture {
    /// The backend fills `sub` with the user's UUID.
    static let userId: UserID = {
        guard let id = UserID(rawValue: "7b1f0f1e-8b9c-4c2a-9a1d-3f5e6c7d8a90") else {
            fatalError("The fixture sub is not a UUID")
        }
        return id
    }()

    /// Unsigned: the app never verifies the signature.
    static func token(email: String?, name: String?, sub: String? = userId.rawValue) -> String {
        var claims: [String: String] = [:]
        claims["sub"] = sub
        claims["email"] = email
        claims["name"] = name
        let payload = (try? JSONSerialization.data(withJSONObject: claims, options: .sortedKeys)) ?? Data()
        let base64url = payload.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "eyJhbGciOiJIUzI1NiJ9.\(base64url).c2ln"
    }
}
