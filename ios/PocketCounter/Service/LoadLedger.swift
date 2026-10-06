import Foundation

/// The ledger fails the screen; a lookup only degrades it. The one policy three screens share.
struct LoadLedger: Sendable {
    let transactions: any TransactionRepository
    let tags: any TagRepository
    let cards: any CreditCardRepository

    private struct LookupReads: Sendable {
        let categories: Result<[TagContext], LoadFailure>
        let tags: Result<[Tag], LoadFailure>
        let cards: Result<[CreditCard], LoadFailure>
    }

    func month(_ ref: RefYearMonth) async throws(LoadFailure) -> MonthLedger {
        async let items = capture { () async throws(LoadFailure) -> [HistoryItem] in try await transactions.month(ref) }
        let reads = await lookups()
        let (read, lookups) = try Self.settle(await items, reads)
        return MonthLedger(ref: ref, items: read, lookups: lookups)
    }

    func range(_ span: RefYearMonthRange) async throws(LoadFailure) -> RangeLedger {
        async let items = capture { () async throws(LoadFailure) -> [HistoryItem] in try await transactions.range(span) }
        let reads = await lookups()
        let (read, lookups) = try Self.settle(await items, reads)
        return RangeLedger(span: span, items: read, lookups: lookups)
    }

    private func lookups() async -> LookupReads {
        async let categories = capture { () async throws(LoadFailure) -> [TagContext] in try await tags.categories() }
        async let tagList = capture { () async throws(LoadFailure) -> [Tag] in try await tags.tags() }
        async let cardList = capture { () async throws(LoadFailure) -> [CreditCard] in try await cards.cards() }
        return await LookupReads(categories: categories, tags: tagList, cards: cardList)
    }

    /// `async let` erases a typed throw to `any Error`, so each read travels as a `Result`.
    private func capture<T: Sendable>(_ read: () async throws(LoadFailure) -> T) async -> Result<T, LoadFailure> {
        do {
            return .success(try await read())
        } catch {
            return .failure(error)
        }
    }

    /// Precedence: session expired (any leg), then the ledger's own failure, then abandoned, then degrade.
    private static func settle<T>(
        _ ledger: Result<T, LoadFailure>,
        _ reads: LookupReads
    ) throws(LoadFailure) -> (T, LookupSet) {
        let categories = degrading(reads.categories)
        let tags = degrading(reads.tags)
        let cards = degrading(reads.cards)
        let lookupFailures = [categories.failure, tags.failure, cards.failure].compactMap { $0 }
        if lookupFailures.contains(.sessionExpired) { throw .sessionExpired }
        let items = try ledger.get()
        if lookupFailures.contains(.abandoned) { throw .abandoned }
        let failed: [(LookupKind, LoadFailure?)] = [
            (.categories, categories.failure), (.tags, tags.failure), (.cards, cards.failure),
        ]
        let lookups = LookupSet(
            categories: categories.value, tags: tags.value, cards: cards.value,
            failed: Set(failed.filter { $0.1 != nil }.map(\.0))
        )
        return (items, lookups)
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
