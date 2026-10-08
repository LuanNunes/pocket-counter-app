/// Where the server says a value came from. `defined` is client-only, so it is not here.
enum FieldSource: Hashable, Sendable {
    case written
    case inferred
}
