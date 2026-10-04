import Foundation
import OSLog
import Security

/// One Keychain item holding the whole pair as JSON, so a rotation is a single atomic write:
/// a new access token can never be paired with an old refresh token.
///
/// The item is deliberately not cleared on reinstall, so the session survives. This
/// diverges from Android, where the DataStore is deleted with the app.
actor KeychainTokenStore: TokenStoring {
    struct KeychainError: Error, Equatable {
        let status: OSStatus
    }

    private let service: String
    private let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "keychain")
    private var cache: TokenPair?
    private var isLoaded = false

    init(scope: String) {
        service = "com.resolveprogramming.pocketcounter.tokens.\(scope)"
    }

    func tokens() -> TokenPair? {
        guard !isLoaded else { return cache }
        cache = load()
        isLoaded = true
        return cache
    }

    func save(_ pair: TokenPair) throws {
        let data = try JSONEncoder().encode(pair)
        let status = SecItemAdd(
            query.merging([
                kSecValueData as String: data,
                kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            ]) { $1 } as CFDictionary,
            nil
        )
        switch status {
        case errSecSuccess:
            break
        case errSecDuplicateItem:
            // Update in place: delete-then-add would leave no session if we died in between.
            let updated = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
            guard updated == errSecSuccess else { throw KeychainError(status: updated) }
        default:
            throw KeychainError(status: status)
        }
        cache = pair
        isLoaded = true
    }

    func clear() {
        let status = SecItemDelete(query as CFDictionary)
        if status != errSecSuccess, status != errSecItemNotFound {
            logger.fault("Keychain delete failed: \(status)")
        }
        cache = nil
        isLoaded = true
    }

    private var query: [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "session",
        ]
    }

    // Logged degradation, not a swallowed error: when attaching a bearer there is no useful
    // recovery, and the right outcome is the login screen.
    private func load() -> TokenPair? {
        var result: CFTypeRef?
        let status = SecItemCopyMatching(
            query.merging([kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]) { $1 } as CFDictionary,
            &result
        )
        switch status {
        case errSecSuccess:
            guard let data = result as? Data, let pair = try? JSONDecoder().decode(TokenPair.self, from: data) else {
                logger.fault("Keychain item is not a token pair")
                return nil
            }
            return pair
        case errSecItemNotFound:
            return nil
        default:
            logger.fault("Keychain read failed: \(status)")
            return nil
        }
    }
}
