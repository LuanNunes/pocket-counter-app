import Foundation
import Security

@testable import PocketCounter

/// Scripted statuses for `KeychainAccess`, and a call counter: the simulator Keychain cannot
/// be asked for `errSecInteractionNotAllowed`, and whether a failed read is retried is only
/// observable by counting.
final class FakeKeychain: @unchecked Sendable {
    typealias Read = (status: OSStatus, data: Data?)

    private let lock = NSLock()
    private var reads: [Read]
    private let writeStatus: OSStatus
    private let removeStatus: OSStatus
    private var readCalls = 0
    private var removeCalls = 0
    private var written: [Data] = []

    /// The last scripted read repeats once the earlier ones are consumed.
    init(reads: [Read], writeStatus: OSStatus = errSecSuccess, removeStatus: OSStatus = errSecSuccess) {
        self.reads = reads
        self.writeStatus = writeStatus
        self.removeStatus = removeStatus
    }

    static func missing(writeStatus: OSStatus = errSecSuccess, removeStatus: OSStatus = errSecSuccess) -> FakeKeychain {
        FakeKeychain(reads: [(errSecItemNotFound, nil)], writeStatus: writeStatus, removeStatus: removeStatus)
    }

    static func holding(
        _ pair: TokenPair, writeStatus: OSStatus = errSecSuccess, removeStatus: OSStatus = errSecSuccess
    ) -> FakeKeychain {
        let data = (try? JSONEncoder().encode(pair)) ?? Data()
        return FakeKeychain(reads: [(errSecSuccess, data)], writeStatus: writeStatus, removeStatus: removeStatus)
    }

    var readCount: Int { lock.withLock { readCalls } }
    var removeCount: Int { lock.withLock { removeCalls } }
    var writes: [Data] { lock.withLock { written } }

    var access: KeychainAccess {
        KeychainAccess(
            read: { [self] _, _ in
                lock.withLock {
                    readCalls += 1
                    return reads.count > 1 ? reads.removeFirst() : reads[0]
                }
            },
            write: { [self] _, _, data in
                lock.withLock {
                    written.append(data)
                    return writeStatus
                }
            },
            remove: { [self] _, _ in
                lock.withLock {
                    removeCalls += 1
                    return removeStatus
                }
            }
        )
    }
}
