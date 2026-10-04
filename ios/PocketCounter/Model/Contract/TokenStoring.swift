protocol TokenStoring: Sendable {
    func tokens() async -> TokenPair?
    func save(_ pair: TokenPair) async throws
    func clear() async
}
