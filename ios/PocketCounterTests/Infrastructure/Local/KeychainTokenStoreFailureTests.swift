import Foundation
import Security
import Testing

@testable import PocketCounter

@Suite("KeychainTokenStore failures")
struct KeychainTokenStoreFailureTests {

    private let first = TokenPair(accessToken: "access-1", refreshToken: "refresh-1")
    private let second = TokenPair(accessToken: "access-2", refreshToken: "refresh-2")

    private func store(_ keychain: FakeKeychain) -> KeychainTokenStore {
        KeychainTokenStore(scope: "fake", access: keychain.access)
    }

    @Test("a read before first unlock throws instead of reporting no session")
    func interactionNotAllowed() async {
        let keychain = FakeKeychain(reads: [(errSecInteractionNotAllowed, nil)])

        await #expect(throws: TokenStoreUnavailable()) { try await store(keychain).tokens() }
    }

    @Test("a failed read is asked again, never remembered as an absent session")
    func failedReadIsNotCached() async throws {
        let keychain = FakeKeychain(reads: [
            (errSecInteractionNotAllowed, nil),
            (errSecSuccess, try JSONEncoder().encode(first)),
        ])
        let store = store(keychain)

        await #expect(throws: TokenStoreUnavailable()) { try await store.tokens() }

        #expect(try await store.tokens() == first)
        #expect(keychain.readCount == 2)
    }

    @Test("an absent item is conclusive and read only once")
    func itemNotFoundIsCached() async throws {
        let keychain = FakeKeychain.missing()
        let store = store(keychain)

        #expect(try await store.tokens() == nil)
        #expect(try await store.tokens() == nil)
        #expect(keychain.readCount == 1)
    }

    @Test("an item that is not a token pair is conclusive: rereading it would not help")
    func undecodableItemIsCached() async throws {
        let keychain = FakeKeychain(reads: [(errSecSuccess, Data("not json".utf8))])
        let store = store(keychain)

        #expect(try await store.tokens() == nil)
        #expect(try await store.tokens() == nil)
        #expect(keychain.readCount == 1)
    }

    @Test("a write that fails throws and is not cached as if it had persisted")
    func failedWriteThrows() async throws {
        let keychain = FakeKeychain.missing(writeStatus: errSecInteractionNotAllowed)
        let store = store(keychain)

        await #expect(throws: TokenStoreUnavailable()) { try await store.save(first) }

        #expect(try await store.tokens() == nil)
    }

    @Test("a write that fails leaves the previously loaded pair in place")
    func failedWriteKeepsLoadedPair() async throws {
        let keychain = FakeKeychain.holding(first, writeStatus: errSecInteractionNotAllowed)
        let store = store(keychain)
        #expect(try await store.tokens() == first)

        await #expect(throws: TokenStoreUnavailable()) { try await store.save(second) }

        #expect(try await store.tokens() == first)
    }

    @Test("a successful write is served from the cache without another read")
    func successfulWriteCaches() async throws {
        let keychain = FakeKeychain.missing()
        let store = store(keychain)

        try await store.save(first)

        #expect(try await store.tokens() == first)
        #expect(keychain.readCount == 0)
        #expect(keychain.writes.count == 1)
    }

    @Test("a delete that fails throws instead of claiming the session is gone")
    func failedDeleteThrows() async {
        let keychain = FakeKeychain.holding(first, removeStatus: errSecInteractionNotAllowed)

        await #expect(throws: TokenStoreUnavailable()) { try await store(keychain).clear() }
    }

    @Test("after a failed delete the pair that is still stored is read again, not served as empty")
    func failedDeleteIsNotCached() async throws {
        let keychain = FakeKeychain.holding(first, removeStatus: errSecInteractionNotAllowed)
        let store = store(keychain)

        await #expect(throws: TokenStoreUnavailable()) { try await store.clear() }

        #expect(try await store.tokens() == first)
        #expect(keychain.readCount == 1)
    }

    @Test("a failed delete leaves an already loaded pair in the cache")
    func failedDeleteKeepsLoadedPair() async throws {
        let keychain = FakeKeychain.holding(first, removeStatus: errSecInteractionNotAllowed)
        let store = store(keychain)
        #expect(try await store.tokens() == first)

        await #expect(throws: TokenStoreUnavailable()) { try await store.clear() }

        #expect(try await store.tokens() == first)
    }

    @Test("deleting an item that is already absent is a success")
    func deleteNotFoundSucceeds() async throws {
        let keychain = FakeKeychain.missing(removeStatus: errSecItemNotFound)
        let store = store(keychain)

        try await store.clear()

        #expect(try await store.tokens() == nil)
        #expect(keychain.removeCount == 1)
        #expect(keychain.readCount == 0)
    }
}
