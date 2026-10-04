@testable import PocketCounter

actor InMemoryTokenStore: TokenStoring {
    private(set) var stored: TokenPair?
    private(set) var readCount = 0
    private var failingReads: Int
    private let failingWrites: Bool
    private let failingClear: Bool

    /// `failingReads` is a countdown consumed per call: "fails before first unlock, works after".
    init(_ pair: TokenPair? = nil, failingReads: Int = 0, failingWrites: Bool = false, failingClear: Bool = false) {
        stored = pair
        self.failingReads = failingReads
        self.failingWrites = failingWrites
        self.failingClear = failingClear
    }

    func tokens() throws(TokenStoreUnavailable) -> TokenPair? {
        readCount += 1
        guard failingReads == 0 else {
            failingReads -= 1
            throw TokenStoreUnavailable()
        }
        return stored
    }

    func save(_ pair: TokenPair) throws(TokenStoreUnavailable) {
        guard !failingWrites else { throw TokenStoreUnavailable() }
        stored = pair
    }

    func clear() throws(TokenStoreUnavailable) {
        guard !failingClear else { throw TokenStoreUnavailable() }
        stored = nil
    }
}
