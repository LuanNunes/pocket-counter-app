import Foundation
import Testing

@testable import PocketCounter

@Suite("KeychainTokenStore")
struct KeychainTokenStoreTests {

    private let scope = "test.\(UUID())"
    private let first = TokenPair(accessToken: "access-1", refreshToken: "refresh-1")
    private let second = TokenPair(accessToken: "access-2", refreshToken: "refresh-2")

    /// `defer` cannot `await`, so the cleanup runs on both paths here instead: a throwing
    /// `save` or a failed `try` would otherwise leak a real Keychain item.
    private func withStore(_ body: (KeychainTokenStore) async throws -> Void) async throws {
        let store = KeychainTokenStore(scope: scope)
        do {
            try await body(store)
        } catch {
            try? await store.clear()
            throw error
        }
        try await store.clear()
    }

    @Test("an empty store has no tokens")
    func empty() async throws {
        try await withStore { store in
            let tokens = try await store.tokens()

            #expect(tokens == nil)
        }
    }

    @Test("a saved pair is read back")
    func roundTrip() async throws {
        try await withStore { store in
            try await store.save(first)

            #expect(try await store.tokens() == first)
        }
    }

    @Test("a saved pair survives a new instance, so it came from the Keychain and not the cache")
    func persists() async throws {
        try await withStore { writer in
            try await writer.save(first)

            let reader = KeychainTokenStore(scope: scope)

            #expect(try await reader.tokens() == first)
        }
    }

    @Test("saving again replaces the whole pair")
    func rotation() async throws {
        try await withStore { store in
            try await store.save(first)

            try await store.save(second)

            #expect(try await store.tokens() == second)
            #expect(try await KeychainTokenStore(scope: scope).tokens() == second)
        }
    }

    @Test("clearing removes the pair from the cache and the Keychain")
    func clear() async throws {
        try await withStore { store in
            try await store.save(first)

            try await store.clear()

            #expect(try await store.tokens() == nil)
            #expect(try await KeychainTokenStore(scope: scope).tokens() == nil)
        }
    }

    @Test("clearing an empty store is harmless")
    func clearEmpty() async throws {
        try await withStore { store in
            try await store.clear()

            let tokens = try await store.tokens()

            #expect(tokens == nil)
        }
    }

    @Test("scopes do not share tokens")
    func scopesIsolated() async throws {
        try await withStore { store in
            try await store.save(first)

            #expect(try await KeychainTokenStore(scope: "test.\(UUID())").tokens() == nil)
        }
    }
}
