@testable import PocketCounter

actor InMemoryTokenStore: TokenStoring {
    private var pair: TokenPair?
    private(set) var saveCount = 0
    private(set) var clearCount = 0

    init(_ pair: TokenPair? = nil) { self.pair = pair }

    func tokens() -> TokenPair? { pair }

    func save(_ pair: TokenPair) {
        self.pair = pair
        saveCount += 1
    }

    func clear() {
        pair = nil
        clearCount += 1
    }
}
