/// What the server made of the card. `notApplicable` does not mean no card was mentioned:
/// *"paguei 50 no credito"* with no cards registered is a credit row that answers it.
enum CardReading: Hashable, Sendable {
    case resolved(Sourced<CardCandidate>)
    case ambiguous([CardCandidate])
    case unresolved
    case notApplicable
}
