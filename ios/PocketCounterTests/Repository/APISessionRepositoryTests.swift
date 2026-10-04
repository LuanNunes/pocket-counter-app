import Foundation
import Testing

@testable import PocketCounter

@Suite("APISessionRepository")
struct APISessionRepositoryTests {

    private let base = URL(string: "https://api-dev.pocket-counter.com/")!
    private let accessToken = JWTFixture.token(email: "ana@b.com", name: "Ana")

    private var tokenResponse: String {
        #"{"accessToken":"\#(accessToken)","refreshToken":"refresh-1","expiresIn":900,"tokenType":"Bearer"}"#
    }

    private func make(_ fake: FakeHTTP, tokens: InMemoryTokenStore = InMemoryTokenStore()) -> APISessionRepository {
        APISessionRepository(client: APIClient(baseURL: base, send: fake.send), tokens: tokens)
    }

    private func login() throws -> LoginCredentials { try LoginCredentials(email: "ana@b.com", password: "secret") }

    @Test("signing in stores the token pair and returns the user from the access token")
    func signIn() async throws {
        let fake = FakeHTTP(FakeHTTP.json(tokenResponse))
        let tokens = InMemoryTokenStore()

        let user = try await make(fake, tokens: tokens).signIn(login())

        #expect(user == AuthenticatedUser(name: "Ana", email: "ana@b.com"))
        #expect(await tokens.tokens() == TokenPair(accessToken: accessToken, refreshToken: "refresh-1"))
        let request = try #require(fake.requests.first)
        #expect(request.url?.path == "/api/v1/auth/login")
        #expect(request.httpMethod == "POST")
        let body = try #require(request.httpBody)
        let json = try #require(try JSONSerialization.jsonObject(with: body) as? [String: String])
        #expect(json == ["email": "ana@b.com", "password": "secret"])
    }

    @Test("the user comes from the token, not from the typed credentials")
    func userFromToken() async throws {
        let token = JWTFixture.token(email: "canonical@b.com", name: "  Bia ")
        let fake = FakeHTTP(FakeHTTP.json(#"{"accessToken":"\#(token)","refreshToken":"r","expiresIn":900,"tokenType":"Bearer"}"#))

        let user = try await make(fake).signIn(login())

        #expect(user == AuthenticatedUser(name: "Bia", email: "canonical@b.com"))
    }

    @Test("a token without an email falls back to the typed one")
    func tokenWithoutEmail() async throws {
        let token = JWTFixture.token(email: nil, name: nil)
        let fake = FakeHTTP(FakeHTTP.json(#"{"accessToken":"\#(token)","refreshToken":"r","expiresIn":900,"tokenType":"Bearer"}"#))

        let user = try await make(fake).signIn(login())

        #expect(user == AuthenticatedUser(name: nil, email: "ana@b.com"))
    }

    private static let envelope = #"{"code":"X","message":"Server says no","details":[],"correlationId":"c","timestamp":"t"}"#

    @Test("sign-in failures are translated by HTTP status", arguments: [
        (401, AuthenticationFailure.invalidCredentials),
        (409, .emailAlreadyRegistered),
        (400, .rejected("Server says no")),
        (422, .rejected("Server says no")),
        (500, .server),
        (503, .server),
    ])
    func statusMap(status: Int, expected: AuthenticationFailure) async throws {
        let fake = FakeHTTP(FakeHTTP.json(Self.envelope, status: status))
        let tokens = InMemoryTokenStore()

        let failure = await signInFailure(make(fake, tokens: tokens))

        #expect(failure == expected)
        #expect(await tokens.tokens() == nil)
    }

    @Test("a transport failure is unreachable")
    func transport() async {
        let fake = FakeHTTP(.failure(URLError(.notConnectedToInternet)))

        #expect(await signInFailure(make(fake)) == .unreachable)
    }

    @Test("a rejection without the error envelope still carries a readable message")
    func rejectedWithoutEnvelope() async {
        let fake = FakeHTTP(FakeHTTP.empty(400))

        #expect(await signInFailure(make(fake)) == .rejected("Não foi possível processar a solicitação"))
    }

    @Test("a malformed success body is a server failure")
    func malformedSuccess() async {
        let fake = FakeHTTP(FakeHTTP.json(#"{"nope":1}"#))

        #expect(await signInFailure(make(fake)) == .server)
    }

    private func signInFailure(_ repository: APISessionRepository) async -> AuthenticationFailure? {
        guard let credentials = try? login() else { return nil }
        do {
            _ = try await repository.signIn(credentials)
            return nil
        } catch {
            return error
        }
    }

    @Test("registering posts email, name and password, accepts a 201 and stores the pair")
    func register() async throws {
        let fake = FakeHTTP(FakeHTTP.json(tokenResponse, status: 201))
        let tokens = InMemoryTokenStore()
        let registration = try Registration(name: "Ana", email: "ana@b.com", password: "12345678")

        let user = try await make(fake, tokens: tokens).register(registration)

        #expect(user == AuthenticatedUser(name: "Ana", email: "ana@b.com"))
        #expect(await tokens.tokens() == TokenPair(accessToken: accessToken, refreshToken: "refresh-1"))
        let request = try #require(fake.requests.first)
        #expect(request.url?.path == "/api/v1/auth/register")
        let body = try #require(request.httpBody)
        let json = try #require(try JSONSerialization.jsonObject(with: body) as? [String: String])
        #expect(json == ["email": "ana@b.com", "name": "Ana", "password": "12345678"])
    }

    @Test("registering with a taken email is reported as such")
    func registerConflict() async throws {
        let fake = FakeHTTP(FakeHTTP.json(Self.envelope, status: 409))
        let registration = try Registration(name: "Ana", email: "ana@b.com", password: "12345678")

        await #expect(throws: AuthenticationFailure.emailAlreadyRegistered) {
            try await make(fake).register(registration)
        }
    }

    @Test("restoring with no stored tokens is signed out")
    func restoreSignedOut() async {
        let fake = FakeHTTP(FakeHTTP.empty(500))

        #expect(await make(fake).restore() == .signedOut)
        #expect(fake.callCount == 0)
    }

    @Test("restoring with stored tokens is signed in as the user in the access token, without network")
    func restoreSignedIn() async {
        let fake = FakeHTTP(FakeHTTP.empty(500))
        let tokens = InMemoryTokenStore(TokenPair(accessToken: accessToken, refreshToken: "r"))

        #expect(await make(fake, tokens: tokens).restore() == .signedIn(AuthenticatedUser(name: "Ana", email: "ana@b.com")))
        #expect(fake.callCount == 0)
    }

    @Test("an unreadable stored access token restores as signed out")
    func restoreGarbage() async {
        let tokens = InMemoryTokenStore(TokenPair(accessToken: "garbage", refreshToken: "r"))

        #expect(await make(FakeHTTP(FakeHTTP.empty(500)), tokens: tokens).restore() == .signedOut)
    }

    @Test("signing out posts the refresh token to logout and clears the tokens")
    func signOut() async throws {
        let fake = FakeHTTP(FakeHTTP.empty(204))
        let tokens = InMemoryTokenStore(TokenPair(accessToken: accessToken, refreshToken: "refresh-1"))

        await make(fake, tokens: tokens).signOut()

        let request = try #require(fake.requests.first)
        #expect(request.url?.path == "/api/v1/auth/logout")
        #expect(request.httpBody.flatMap { String(data: $0, encoding: .utf8) } == #"{"token":"refresh-1"}"#)
        #expect(await tokens.tokens() == nil)
    }

    @Test("signing out clears the tokens even when the logout call fails", arguments: [
        FakeHTTP.empty(500), FakeHTTP.Reply.failure(URLError(.notConnectedToInternet)),
    ])
    func signOutFailing(reply: FakeHTTP.Reply) async {
        let tokens = InMemoryTokenStore(TokenPair(accessToken: accessToken, refreshToken: "r"))

        await make(FakeHTTP(reply), tokens: tokens).signOut()

        #expect(await tokens.tokens() == nil)
    }

    @Test("signing out with no tokens makes no request")
    func signOutWithoutTokens() async {
        let fake = FakeHTTP(FakeHTTP.empty(204))

        await make(fake).signOut()

        #expect(fake.callCount == 0)
    }
}
