/// The badge a field wears: *da frase*, *assumido* or *definido*.
enum FieldProvenance: Hashable, Sendable {
    case fromSentence
    case assumed
    /// Only a draft edit produces it; the server never sends it.
    case defined

    init(_ source: FieldSource) {
        switch source {
        case .written: self = .fromSentence
        case .inferred: self = .assumed
        }
    }
}
