import Foundation
import Testing

@testable import PocketCounter

@Suite("KeychainTokenStore")
struct KeychainTokenStoreTests {

    private let scope = "test.\(UUID())"
    private let first = TokenPair(accessToken: "access-1", refreshToken: "refresh-1")
    private let second = TokenPair(accessToken: "access-2", refreshToken: "refresh-2")

    @Test("an empty store has no tokens")
    func empty() async {
        let store = KeychainTokenStore(scope: scope)

        #expect(await store.tokens() == nil)
    }

    @Test("a saved pair is read back")
    func roundTrip() async throws {
        let store = KeychainTokenStore(scope: scope)

        try await store.save(first)

        #expect(await store.tokens() == first)

        await store.clear()
    }

    @Test("a saved pair survives a new instance, so it came from the Keychain and not the cache")
    func persists() async throws {
        let writer = KeychainTokenStore(scope: scope)
        try await writer.save(first)

        let reader = KeychainTokenStore(scope: scope)

        #expect(await reader.tokens() == first)

        await writer.clear()
    }

    @Test("saving again replaces the whole pair")
    func rotation() async throws {
        let store = KeychainTokenStore(scope: scope)
        try await store.save(first)

        try await store.save(second)

        #expect(await store.tokens() == second)
        #expect(await KeychainTokenStore(scope: scope).tokens() == second)

        await store.clear()
    }

    @Test("clearing removes the pair from the cache and the Keychain")
    func clear() async throws {
        let store = KeychainTokenStore(scope: scope)
        try await store.save(first)

        await store.clear()

        #expect(await store.tokens() == nil)
        #expect(await KeychainTokenStore(scope: scope).tokens() == nil)
    }

    @Test("clearing an empty store is harmless")
    func clearEmpty() async {
        let store = KeychainTokenStore(scope: scope)

        await store.clear()

        #expect(await store.tokens() == nil)
    }

    @Test("scopes do not share tokens")
    func scopesIsolated() async throws {
        let store = KeychainTokenStore(scope: scope)
        try await store.save(first)

        #expect(await KeychainTokenStore(scope: "test.\(UUID())").tokens() == nil)

        await store.clear()
    }
}
