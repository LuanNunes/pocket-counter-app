/// A reading with the lookups the sheet needs to let the user correct it.
/// `lookups.categories` is deliberately empty: nothing in the sheet groups by context, so
/// here an empty list does not mean the lookup failed.
struct SentenceOpening: Hashable, Sendable {
    let reading: SentenceReading
    let lookups: LookupSet
}
