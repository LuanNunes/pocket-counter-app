import Testing

@testable import PocketCounter

@Suite("PocketCardPosition")
struct PocketCardPositionTests {

    @Test("a position follows the row's place in its group", arguments: [
        (0, 0, PocketCardPosition.only), (0, 1, .only), (0, 3, .first), (1, 3, .middle), (2, 3, .last), (1, 2, .last),
    ])
    func position(index: Int, count: Int, expected: PocketCardPosition) {
        #expect(PocketCardPosition.of(index: index, count: count) == expected)
    }
}
