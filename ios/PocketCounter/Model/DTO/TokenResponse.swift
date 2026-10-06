/// The backend's `TokenResponseDto`, answered by login, register and refresh alike.
/// Only the two tokens are required: the rest is informational, and a response the app can
/// act on must not fail to decode over a field it ignores.
struct TokenResponse: Codable, Sendable {
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int?
    let tokenType: String?
    let tutorialCompleted: Bool?
}
