import Testing

@testable import PocketCounter

@Suite("HistoryItem.effectiveTagIds")
struct EffectiveTagIdsTests {
    private let own = [TagID(rawValue: "x")]
    private let inherited = [TagID(rawValue: "a"), TagID(rawValue: "b")]

    @Test("no own tags inherit")
    func inherits() {
        #expect(HistoryItem.fixture(tagIds: nil).effectiveTagIds(inheriting: inherited) == inherited)
    }

    @Test("an empty own list overrides the inherited tags")
    func emptyOverride() {
        #expect(HistoryItem.fixture(tagIds: []).effectiveTagIds(inheriting: inherited) == [])
    }

    @Test("own tags override the inherited tags")
    func override() {
        #expect(HistoryItem.fixture(tagIds: own).effectiveTagIds(inheriting: inherited) == own)
    }

    @Test("inheriting nothing yields nothing")
    func nothingInherited() {
        #expect(HistoryItem.fixture(tagIds: nil).effectiveTagIds(inheriting: []) == [])
    }
}
