import Foundation
import Testing

@testable import PocketCounter

@Suite("ReadSentence")
struct ReadSentenceTests {
    private let card = CreditCard(id: CardID(rawValue: "k1"), name: "Nubank", brand: nil, closingDay: nil, color: nil)

    private func open(
        sentence: Result<SentenceReading, ReadingFailure> = .success(.fixture()),
        tags: Result<[PocketCounter.Tag], LoadFailure> = .success([]),
        cards: Result<[CreditCard], LoadFailure>? = nil
    ) async throws -> SentenceOpening {
        let read = ReadSentence(
            sentences: FakeSentenceReadingRepository(result: sentence),
            tags: FakeTagRepository(tagsResult: tags),
            cards: FakeCreditCardRepository(cardsResult: cards ?? .success([card]))
        )
        return try await read.reading(of: try SentenceText("gastei 250 numa consulta"), on: .fixture)
    }

    @Test("the happy path carries the reading and both lookups")
    func happyPath() async throws {
        let opening = try await open(tags: .success([.fixture("g1", "Mercado", kind: .expense)]))

        #expect(opening.reading.amount?.value == Money(250))
        #expect(opening.lookups.tags.count == 1)
        #expect(opening.lookups.cards == [card])
        #expect(opening.lookups.failed.isEmpty)
    }

    @Test("the sentence and both lookups are in flight together")
    func concurrent() async throws {
        let rendezvous = Rendezvous()
        let read = ReadSentence(
            sentences: FakeSentenceReadingRepository(rendezvous: rendezvous),
            tags: FakeTagRepository(rendezvous: rendezvous),
            cards: FakeCreditCardRepository(rendezvous: rendezvous)
        )
        let text = try SentenceText("gastei 250 numa consulta")

        let opening = Task { try await read.reading(of: text, on: .fixture) }
        await rendezvous.untilArrivals(3)
        let arrived = rendezvous.arrivals
        await rendezvous.release()
        _ = try await opening.value

        #expect(arrived == 3)
    }

    @Test("a lookup that fails only narrows the sheet")
    func lookupDegrades() async throws {
        let opening = try await open(cards: .failure(.unreachable))

        #expect(opening.lookups.cards.isEmpty)
        #expect(opening.lookups.failed == [.cards])
    }

    @Test("both lookups failing still opens the sheet")
    func bothLookupsDegrade() async throws {
        let opening = try await open(tags: .failure(.server), cards: .failure(.unreachable))

        #expect(opening.lookups.failed == [.tags, .cards])
        #expect(opening.reading.amount?.value == Money(250))
    }

    @Test("the sentence's own failure fails the sheet")
    func sentenceFails() async {
        await #expect(throws: ReadingFailure.tooManyRequests) {
            try await open(sentence: .failure(.tooManyRequests))
        }
    }

    @Test("a lookup's lost session beats the sentence's own failure")
    func sessionExpiredWins() async {
        await #expect(throws: ReadingFailure.sessionExpired) {
            try await open(sentence: .failure(.server), cards: .failure(.sessionExpired))
        }
    }

    /// `LoadLedger` throws on `.abandoned`; here it degrades. The sentence is the user's
    /// action, and a cancelled lookup is no reason to refuse to open the sheet.
    @Test("a lookup that was abandoned or not found degrades like any other", arguments: [
        LoadFailure.abandoned, LoadFailure.notFound,
    ])
    func otherLoadFailuresDegrade(failure: LoadFailure) async throws {
        let opening = try await open(cards: .failure(failure))

        #expect(opening.lookups.failed == [.cards])
        #expect(opening.reading.amount?.value == Money(250))
    }
}
