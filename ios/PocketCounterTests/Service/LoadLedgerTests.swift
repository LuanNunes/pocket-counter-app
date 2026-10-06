import Foundation
import Testing

@testable import PocketCounter

@Suite("LoadLedger")
struct LoadLedgerTests {
    private let ref = RefYearMonth(raw: 202610)!
    private let tag = Tag(id: TagID(rawValue: "g1"), name: "Mercado", kind: .expense)
    private let card = CreditCard(id: CardID(rawValue: "k1"), name: "Nubank", brand: nil, closingDay: nil, color: nil)
    private let category = TagContext(id: ContextID(rawValue: "c1"), name: "Casa", color: nil)

    private func useCase(
        transactions: FakeTransactionRepository = .init(),
        tags: FakeTagRepository = .init(),
        cards: FakeCreditCardRepository = .init()
    ) -> LoadLedger {
        LoadLedger(transactions: transactions, tags: tags, cards: cards)
    }

    @Test("a month carries its items and every lookup")
    func monthLoads() async throws {
        let items = [HistoryItem.fixture(id: "t1")]
        let useCase = useCase(
            transactions: .init(monthResult: .success(items)),
            tags: .init(tagsResult: .success([tag]), categoriesResult: .success([category])),
            cards: .init(cardsResult: .success([card]))
        )

        let ledger = try await useCase.month(ref)

        #expect(ledger == MonthLedger(
            ref: ref, items: items,
            lookups: LookupSet(categories: [category], tags: [tag], cards: [card])
        ))
    }

    @Test("a range carries its span, items and lookups")
    func rangeLoads() async throws {
        let span = try RefYearMonthRange(from: ref, through: ref)
        let items = [HistoryItem.fixture(id: "t1")]
        let useCase = useCase(transactions: .init(rangeResult: .success(items)), cards: .init(cardsResult: .success([card])))

        let ledger = try await useCase.range(span)

        #expect(ledger.span == span)
        #expect(ledger.items == items)
        #expect(ledger.lookups.cards == [card])
        #expect(ledger.lookups.failed.isEmpty)
    }

    @Test("the ledger and the three lookups are all in flight together")
    func concurrent() async throws {
        let rendezvous = Rendezvous()
        let useCase = useCase(
            transactions: .init(rendezvous: rendezvous), tags: .init(rendezvous: rendezvous),
            cards: .init(rendezvous: rendezvous)
        )

        let load = Task { try await useCase.month(ref) }
        await rendezvous.untilArrivals(4)
        let arrived = rendezvous.arrivals
        await rendezvous.release()
        _ = try await load.value

        #expect(arrived == 4)
    }

    @Test("a lookup failure degrades the lookups and leaves the ledger intact", arguments: [
        LoadFailure.unreachable, .server, .notFound, .rejected("x"), .authenticationUnavailable,
    ])
    func degrades(failure: LoadFailure) async throws {
        let items = [HistoryItem.fixture(id: "t1")]
        let useCase = useCase(
            transactions: .init(monthResult: .success(items)),
            tags: .init(tagsResult: .failure(failure), categoriesResult: .success([category])),
            cards: .init(cardsResult: .success([card]))
        )

        let ledger = try await useCase.month(ref)

        #expect(ledger.items == items)
        #expect(ledger.lookups.failed == [.tags])
        #expect(ledger.lookups.tags.isEmpty)
        #expect(ledger.lookups.cards == [card])
    }

    @Test("each failed lookup is named, and only the failed ones")
    func namesTheFailedLookups() async throws {
        let useCase = useCase(
            tags: .init(tagsResult: .success([tag]), categoriesResult: .failure(.server)),
            cards: .init(cardsResult: .failure(.unreachable))
        )

        let ledger = try await useCase.month(ref)

        #expect(ledger.lookups.failed == [.categories, .cards])
        #expect(ledger.lookups.tags == [tag])
    }

    @Test("a lookup that says the session is dead or nobody is waiting is rethrown", arguments: [
        LoadFailure.sessionExpired, .abandoned,
    ])
    func rethrown(failure: LoadFailure) async {
        let useCase = useCase(cards: .init(cardsResult: .failure(failure)))

        await #expect(throws: failure) { try await useCase.month(ref) }
    }

    @Test("a ledger failure fails the load, even when the lookups succeed")
    func ledgerFailure() async {
        let useCase = useCase(transactions: .init(monthResult: .failure(.unreachable)))

        await #expect(throws: LoadFailure.unreachable) { try await useCase.month(ref) }
    }

    @Test("failure precedence: session expired, then ledger failure, then abandoned", arguments: [
        (LoadFailure.sessionExpired, LoadFailure.server, LoadFailure.sessionExpired),
        (.sessionExpired, .abandoned, .sessionExpired),
        (.abandoned, .server, .server),
        (.abandoned, .sessionExpired, .sessionExpired),
        (.abandoned, .abandoned, .abandoned),
    ] as [(LoadFailure, LoadFailure, LoadFailure)])
    func precedence(lookup: LoadFailure, ledger: LoadFailure, expected: LoadFailure) async {
        let useCase = useCase(
            transactions: .init(monthResult: .failure(ledger)), cards: .init(cardsResult: .failure(lookup))
        )

        await #expect(throws: expected) { try await useCase.month(ref) }
    }

    @Test("a range ledger failure fails the load")
    func rangeLedgerFailure() async throws {
        let span = try RefYearMonthRange(from: ref, through: ref)
        let useCase = useCase(transactions: .init(rangeResult: .failure(.server)))

        await #expect(throws: LoadFailure.server) { try await useCase.range(span) }
    }

    @Test("an empty month is empty and not failed")
    func emptyIsNotFailed() async throws {
        let ledger = try await useCase().month(ref)

        #expect(ledger.items == [])
        #expect(ledger.lookups.failed.isEmpty)
    }
}
