/// Carries the server's name, so the card question survives a failed local cards lookup.
struct CardCandidate: Hashable, Sendable {
    let id: CardID
    let name: String
}
