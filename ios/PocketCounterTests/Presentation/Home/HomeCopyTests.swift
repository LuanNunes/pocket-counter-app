import Testing

@testable import PocketCounter

@Suite("HomeCopy")
struct HomeCopyTests {

    @Test("an unknown card count is a dash")
    func unknownCardCount() {
        #expect(HomeCopy.cardCount(nil) == HomeCopy.unknownFigure)
        #expect(HomeCopy.unknownFigure == "—")
    }

    @Test("the card count pluralises", arguments: [(0, "0 cartões"), (1, "1 cartão"), (2, "2 cartões")])
    func cardCount(count: Int, expected: String) {
        #expect(HomeCopy.cardCount(count) == expected)
    }

    @Test("the spoken card count is never a dash")
    func cardCountLabel() {
        #expect(HomeCopy.cardCountLabel(nil) != HomeCopy.unknownFigure)
        #expect(!HomeCopy.cardCountLabel(nil).isEmpty)
        #expect(HomeCopy.cardCountLabel(2) == "2 cartões")
    }

    @Test("the transaction and pending counts")
    func counts() {
        #expect(HomeCopy.transactionCount(12) == "12 no mês")
        #expect(HomeCopy.pendingCount(3) == "3 em aberto")
    }

    @Test("the hero caption pluralises", arguments: [(0, "0 lançs."), (1, "1 lanç."), (2, "2 lançs.")])
    func entryCount(count: Int, expected: String) {
        #expect(HomeCopy.entryCount(count) == expected)
    }

    @Test("the spoken hero count pluralises", arguments: [(0, "0 lançamentos"), (1, "1 lançamento"), (2, "2 lançamentos")])
    func spokenEntryCount(count: Int, expected: String) {
        #expect(HomeCopy.spokenEntryCount(count) == expected)
    }
}
