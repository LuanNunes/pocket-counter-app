import Foundation

enum JWTFixture {
    /// Unsigned: the app never verifies the signature.
    static func token(email: String?, name: String?) -> String {
        var claims: [String: String] = ["sub": "1"]
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
