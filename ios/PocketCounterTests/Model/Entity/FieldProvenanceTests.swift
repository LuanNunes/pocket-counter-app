import Testing

@testable import PocketCounter

@Suite("FieldProvenance")
struct FieldProvenanceTests {
    @Test("a wire source widens to its sentence-side provenance")
    func widening() {
        #expect(FieldProvenance(.written) == .fromSentence)
        #expect(FieldProvenance(.inferred) == .assumed)
    }
}
