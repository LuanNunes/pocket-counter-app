import Foundation
import Testing

@testable import PocketCounter

@MainActor
@Suite("AppContainer")
struct AppContainerTests {

    private func container(_ environment: String, keychain: FakeKeychain, send: FakeHTTP = FakeHTTP(FakeHTTP.empty(500))) throws -> AppContainer {
        let configuration = try AppConfiguration(environmentName: environment, baseURLString: "https://h.com/")
        return AppContainer(configuration: configuration, keychain: keychain.access, send: send.send)
    }

    @Test("the Keychain service is scoped by environment", arguments: ["local", "dev", "prod"])
    func scopesKeychainByEnvironment(environment: String) async throws {
        let keychain = FakeKeychain.missing()

        _ = try await container(environment, keychain: keychain).sessionRepository.restore()

        #expect(keychain.services == ["com.resolveprogramming.pocketcounter.tokens.\(environment)"])
    }

    @Test("a sign-in is visible to a restore on the same container: one token store")
    func sharedStore() async throws {
        let token = JWTFixture.token(email: "ana@b.com", name: "Ana")
        let http = FakeHTTP(FakeHTTP.json(#"{"accessToken":"\#(token)","refreshToken":"r1","expiresIn":900,"tokenType":"Bearer"}"#))
        let keychain = FakeKeychain.missing()
        let container = try container("dev", keychain: keychain, send: http)
        let credentials = try LoginCredentials(email: "ana@b.com", password: "secret")

        let user = try await container.sessionRepository.signIn(credentials)
        let restored = await container.sessionRepository.restore()

        #expect(restored == .signedIn(user))
        #expect(keychain.readCount == 0)
    }
}
