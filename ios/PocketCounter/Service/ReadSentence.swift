import Foundation

/// `LoadLedger`'s policy on a sentence: the sentence fails the sheet, a lookup only narrows it.
struct ReadSentence: Sendable {
    let sentences: any SentenceReadingRepository
    let tags: any TagRepository
    let cards: any CreditCardRepository

    func reading(of text: SentenceText, on day: CalendarDay) async throws(ReadingFailure) -> SentenceOpening {
        async let read = captureReading(of: text, on: day)
        async let tagList = capture { () async throws(LoadFailure) -> [Tag] in try await tags.tags() }
        async let cardList = capture { () async throws(LoadFailure) -> [CreditCard] in try await cards.cards() }
        return try Self.settle(await read, await tagList, await cardList)
    }

    /// `async let` erases a typed throw to `any Error`, so each read travels as a `Result`.
    private func captureReading(
        of text: SentenceText, on day: CalendarDay
    ) async -> Result<SentenceReading, ReadingFailure> {
        do {
            return .success(try await sentences.reading(of: text, on: day))
        } catch {
            return .failure(error)
        }
    }

    private func capture<T: Sendable>(_ read: () async throws(LoadFailure) -> T) async -> Result<T, LoadFailure> {
        do {
            return .success(try await read())
        } catch {
            return .failure(error)
        }
    }

    /// Precedence: a lost session on any leg, then the sentence's own failure, then degrade.
    /// Unlike `LoadLedger`, `.abandoned` degrades: a cancelled lookup must not refuse the sheet.
    private static func settle(
        _ read: Result<SentenceReading, ReadingFailure>,
        _ tags: Result<[Tag], LoadFailure>,
        _ cards: Result<[CreditCard], LoadFailure>
    ) throws(ReadingFailure) -> SentenceOpening {
        let tagList = degrading(tags)
        let cardList = degrading(cards)
        let lookupFailures = [tagList.failure, cardList.failure].compactMap { $0 }
        if lookupFailures.contains(.sessionExpired) { throw .sessionExpired }
        let reading = try read.get()
        let failed: [(LookupKind, LoadFailure?)] = [(.tags, tagList.failure), (.cards, cardList.failure)]
        return SentenceOpening(
            reading: reading,
            lookups: LookupSet(
                categories: [], tags: tagList.value, cards: cardList.value,
                failed: Set(failed.filter { $0.1 != nil }.map(\.0))
            )
        )
    }

    private static func degrading<T>(_ result: Result<[T], LoadFailure>) -> (value: [T], failure: LoadFailure?) {
        switch result {
        case .success(let value):
            return (value, nil)
        case .failure(let failure):
            return ([], failure)
        }
    }
}
