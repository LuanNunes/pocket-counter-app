import Foundation
import OSLog
import Security

/// One Keychain item holding the whole pair as JSON, so a rotation is a single atomic write:
/// a new access token can never be paired with an old refresh token.
///
/// The item is deliberately not cleared on reinstall, so the session survives. This
/// diverges from Android, where the DataStore is deleted with the app.
actor KeychainTokenStore: TokenStoring {
    private static let account = "session"

    private let service: String
    private let access: KeychainAccess
    private let logger = Logger(subsystem: "com.resolveprogramming.pocketcounter", category: "keychain")
    private var cache: TokenPair?
    private var isLoaded = false

    init(scope: String, access: KeychainAccess = .live) {
        service = "com.resolveprogramming.pocketcounter.tokens.\(scope)"
        self.access = access
    }

    func tokens() throws(TokenStoreUnavailable) -> TokenPair? {
        guard !isLoaded else { return cache }
        cache = try load()
        isLoaded = true
        return cache
    }

    func save(_ pair: TokenPair) throws(TokenStoreUnavailable) {
        let data: Data
        do {
            data = try JSONEncoder().encode(pair)
        } catch {
            logger.fault("Token pair could not be encoded: \(error)")
            throw TokenStoreUnavailable()
        }
        let status = access.write(service, Self.account, data)
        guard status == errSecSuccess else {
            logger.fault("Keychain write failed: \(status)")
            throw TokenStoreUnavailable()
        }
        cache = pair
        isLoaded = true
    }

    func clear() throws(TokenStoreUnavailable) {
        let status = access.remove(service, Self.account)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            logger.fault("Keychain delete failed: \(status)")
            throw TokenStoreUnavailable()
        }
        cache = nil
        isLoaded = true
    }

    /// `nil` is a conclusive "no session"; a throw means "cannot tell", and is never cached.
    private func load() throws(TokenStoreUnavailable) -> TokenPair? {
        let (status, data) = access.read(service, Self.account)
        switch status {
        case errSecSuccess:
            guard let data, let pair = try? JSONDecoder().decode(TokenPair.self, from: data) else {
                logger.fault("Keychain item is not a token pair")
                return nil
            }
            return pair
        case errSecItemNotFound:
            return nil
        default:
            logger.fault("Keychain read failed: \(status)")
            throw TokenStoreUnavailable()
        }
    }
}
