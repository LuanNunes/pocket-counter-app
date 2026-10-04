/// The store could not be read, written or cleared. **Not** the same as having no session: the
/// stored pair may well be intact and readable later (a read before first unlock returns
/// `errSecInteractionNotAllowed`). Treating this as "signed out" discards a valid session. A failed clear is the mirror image: the pair is still stored, so
/// reporting the session as ended would bring it back at the next launch.
struct TokenStoreUnavailable: Error, Equatable, Sendable {}

protocol TokenStoring: Sendable {
    func tokens() async throws(TokenStoreUnavailable) -> TokenPair?
    func save(_ pair: TokenPair) async throws(TokenStoreUnavailable)
    func clear() async throws(TokenStoreUnavailable)
}
