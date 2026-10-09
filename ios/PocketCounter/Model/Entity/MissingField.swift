/// What the server could not read, in the questions the user is asked.
enum MissingField: Hashable, Sendable {
    case amount
    case description
    /// The only skippable one.
    case card
    case type
}
