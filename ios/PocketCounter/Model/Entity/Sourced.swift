/// A value the server read, never apart from where it came from: the wire ships the two as
/// parallel optionals, which can say "value with no source" and "source with no value".
struct Sourced<Value: Hashable & Sendable>: Hashable, Sendable {
    let value: Value
    let source: FieldSource
}
