/// The Keychain payload, not a wire shape: `Codable` because the item holds it as JSON.
struct TokenPair: Sendable, Equatable, Codable {
    let accessToken: String
    let refreshToken: String
}
