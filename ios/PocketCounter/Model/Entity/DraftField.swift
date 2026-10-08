/// A value in the working copy, with the badge it wears.
struct DraftField<Value: Hashable & Sendable>: Hashable, Sendable {
    let value: Value
    let provenance: FieldProvenance

    init(value: Value, provenance: FieldProvenance) {
        self.value = value
        self.provenance = provenance
    }

    init(_ sourced: Sourced<Value>) {
        self.init(value: sourced.value, provenance: FieldProvenance(sourced.source))
    }

    static func defined(_ value: Value) -> DraftField {
        DraftField(value: value, provenance: .defined)
    }
}
