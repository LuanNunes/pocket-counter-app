import Foundation
import Security

/// The Keychain as three closures, mirroring `HTTPSend`. A test can hand the store the
/// statuses the simulator Keychain cannot be made to produce — `errSecInteractionNotAllowed`
/// above all, which is what a read before first unlock answers.
struct KeychainAccess: Sendable {
    let read: @Sendable (_ service: String, _ account: String) -> (status: OSStatus, data: Data?)
    let write: @Sendable (_ service: String, _ account: String, _ data: Data) -> OSStatus
    let remove: @Sendable (_ service: String, _ account: String) -> OSStatus
}

extension KeychainAccess {
    static let live = KeychainAccess(
        read: { service, account in
            var result: CFTypeRef?
            let status = SecItemCopyMatching(
                item(service, account).merging([
                    kSecReturnData as String: true,
                    kSecMatchLimit as String: kSecMatchLimitOne,
                ]) { $1 } as CFDictionary,
                &result
            )
            return (status, result as? Data)
        },
        write: { service, account, data in
            let item = item(service, account)
            let added = SecItemAdd(
                item.merging([
                    kSecValueData as String: data,
                    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                ]) { $1 } as CFDictionary,
                nil
            )
            guard added == errSecDuplicateItem else { return added }
            // Update in place: delete-then-add would leave no session if we died in between.
            return SecItemUpdate(item as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        },
        remove: { service, account in SecItemDelete(item(service, account) as CFDictionary) }
    )

    private static func item(_ service: String, _ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
