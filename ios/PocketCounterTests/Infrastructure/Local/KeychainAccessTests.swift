import Foundation
import Security
import Testing

@testable import PocketCounter

/// Against the real Keychain: `.live` is where the `SecItem*` semantics live, so only the
/// real thing can tell whether the duplicate-item fallback is right.
@Suite("KeychainAccess.live")
struct KeychainAccessTests {

    private let account = "session"
    private let service = "test.\(UUID())"
    private let access = KeychainAccess.live

    @Test("a written item reads back")
    func roundTrip() {
        defer { _ = access.remove(service, account) }

        #expect(access.write(service, account, Data("one".utf8)) == errSecSuccess)

        let (status, data) = access.read(service, account)
        #expect(status == errSecSuccess)
        #expect(data == Data("one".utf8))
    }

    @Test("writing over an existing item updates it instead of failing as a duplicate")
    func duplicateUpdatesInPlace() {
        defer { _ = access.remove(service, account) }
        #expect(access.write(service, account, Data("one".utf8)) == errSecSuccess)

        #expect(access.write(service, account, Data("two".utf8)) == errSecSuccess)

        #expect(access.read(service, account).data == Data("two".utf8))
    }

    @Test("an item that was never written is reported as missing, with no data")
    func missingItem() {
        let (status, data) = access.read(service, account)

        #expect(status == errSecItemNotFound)
        #expect(data == nil)
    }

    @Test("removing an item makes the next read report it missing")
    func remove() {
        defer { _ = access.remove(service, account) }
        #expect(access.write(service, account, Data("one".utf8)) == errSecSuccess)

        #expect(access.remove(service, account) == errSecSuccess)

        #expect(access.read(service, account).status == errSecItemNotFound)
    }

    @Test("services do not share items")
    func servicesIsolated() {
        defer { _ = access.remove(service, account) }
        #expect(access.write(service, account, Data("one".utf8)) == errSecSuccess)

        #expect(access.read("test.\(UUID())", account).status == errSecItemNotFound)
    }
}
