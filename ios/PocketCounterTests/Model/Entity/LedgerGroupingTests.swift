import Foundation
import Testing

@testable import PocketCounter

@Suite("LedgerGrouping")
struct LedgerGroupingTests {
    @Suite("Lista")
    struct Lista {
        @Test("rows bucket by day, newest day first, with an absolute subtotal")
        func days() {
            let items = [
                HistoryItem.fixture(id: "a", date: .of(2026, 10, 1), amount: -10),
                .fixture(id: "b", date: .of(2026, 10, 3), amount: -5),
                .fixture(id: "c", date: .of(2026, 10, 1), amount: -2),
            ]

            let groups = LedgerGrouping.groups(of: items, lookups: .fixture(), mode: .lista, kind: .expense)

            #expect(groups.map(\.identity) == [.day(.of(2026, 10, 3)), .day(.of(2026, 10, 1))])
            #expect(groups.map(\.subtotal) == [Money(5), Money(12)])
            #expect(groups[1].items.map(\.id.rawValue) == ["a", "c"])
        }

        @Test("no rows, no groups")
        func empty() {
            #expect(LedgerGrouping.groups(of: [], lookups: .fixture(), mode: .lista, kind: .expense) == [])
        }
    }

    @Suite("Categoria, despesas")
    struct CategoriaExpenses {
        private let lookups = LookupSet.fixture(
            categories: [.fixture("casa"), .fixture("lazer")],
            tags: [
                .fixture("luz", context: "casa"), .fixture("cinema", context: "lazer"),
                .fixture("avulsa"),
            ]
        )

        private func groups(_ items: [HistoryItem], _ lookups: LookupSet? = nil) -> [LedgerGroup] {
            LedgerGrouping.groups(of: items, lookups: lookups ?? self.lookups, mode: .categoria, kind: .expense)
        }

        @Test("rows bucket by the first tag's category, in the categories' display order, with none last")
        func order() {
            let items = [
                HistoryItem.fixture(id: "a", tagIds: []),
                .fixture(id: "b", tagIds: [.of("cinema")]),
                .fixture(id: "c", tagIds: [.of("luz")]),
                .fixture(id: "d", tagIds: [.of("luz")]),
            ]

            let result = groups(items)

            #expect(result.map(\.identity) == [.category(.of("casa")), .category(.of("lazer")), .category(nil)])
            #expect(result[0].items.map(\.id.rawValue) == ["c", "d"])
        }

        @Test("a row with several tags lands only in its first tag's group")
        func neverDuplicated() {
            let result = groups([.fixture(tagIds: [.of("cinema"), .of("luz")])])

            #expect(result.map(\.identity) == [.category(.of("lazer"))])
        }

        @Test("a tag without a category and absent tags both fall to none", arguments: [
            [TagID.of("avulsa")], [],
        ])
        func none(_ tags: [TagID]) {
            #expect(groups([.fixture(tagIds: tags)]).map(\.identity) == [.category(nil)])
        }

        @Test("nil tags inherit nothing yet, so they fall to none")
        func inheritedNothing() {
            #expect(groups([.fixture(tagIds: nil)]).map(\.identity) == [.category(nil)])
        }

        @Test("a tag the lookup does not know is unresolved, not none")
        func unknownTag() {
            #expect(groups([.fixture(tagIds: [.of("gone")])]).map(\.identity) == [.unresolved])
        }

        @Test("a tag whose category is unknown keeps that category's id, apart from none")
        func unknownCategory() {
            let lookups = LookupSet.fixture(categories: [], tags: [.fixture("luz", context: "casa")])
            let items = [HistoryItem.fixture(id: "a", tagIds: [.of("luz")]), .fixture(id: "b", tagIds: [])]

            #expect(groups(items, lookups).map(\.identity) == [.category(.of("casa")), .category(nil)])
        }

        /// `aaa` sorts before `zzz` by id, so only a rank past the known categories puts it last.
        @Test("an unknown category ranks after every known one")
        func unknownCategoryRanksLast() {
            let lookups = LookupSet.fixture(
                categories: [.fixture("zzz")],
                tags: [.fixture("known", context: "zzz"), .fixture("orphan", context: "aaa")]
            )
            let items = [HistoryItem.fixture(id: "a", tagIds: [.of("orphan")]), .fixture(id: "b", tagIds: [.of("known")])]

            #expect(groups(items, lookups).map(\.identity) == [.category(.of("zzz")), .category(.of("aaa"))])
        }

        @Test("when a needed lookup failed, tagged rows are unresolved, ordered last, and untagged rows stay none",
              arguments: [LookupKind.tags, .categories])
        func degraded(_ failed: LookupKind) {
            let lookups = LookupSet.fixture(failed: [failed])
            let items = [
                HistoryItem.fixture(id: "a", tagIds: [.of("luz")]),
                .fixture(id: "b", tagIds: []),
            ]

            #expect(groups(items, lookups).map(\.identity) == [.category(nil), .unresolved])
        }

        @Test("a failed cards lookup does not degrade categories")
        func cardsIrrelevant() {
            let lookups = LookupSet.fixture(categories: [.fixture("casa")], tags: [.fixture("luz", context: "casa")], failed: [.cards])

            #expect(groups([.fixture(tagIds: [.of("luz")])], lookups).map(\.identity) == [.category(.of("casa"))])
        }
    }

    @Suite("Categoria, receitas")
    struct CategoriaIncomes {
        private func groups(_ items: [HistoryItem], _ lookups: LookupSet = .fixture()) -> [LedgerGroup] {
            LedgerGrouping.groups(of: items, lookups: lookups, mode: .categoria, kind: .income)
        }

        private func income(_ id: String, _ name: String, _ amount: Decimal) -> HistoryItem {
            .fixture(id: id, amount: amount, type: .income, name: name)
        }

        @Test("rows bucket by title, largest subtotal first")
        func bySubtotal() {
            let items = [income("a", "Freela", 100), income("b", "Salário", 500), income("c", "Freela", 150)]

            let result = groups(items)

            #expect(result.map(\.identity) == [.incomeName("Salário"), .incomeName("Freela")])
            #expect(result.map(\.subtotal) == [Money(500), Money(250)])
        }

        @Test("a blank title groups under the placeholder title")
        func blankTitle() {
            #expect(groups([income("a", " ", 10)]).map(\.identity) == [.incomeName("—")])
        }

        @Test("equal subtotals order by name, whatever the input order", arguments: [
            ["Zeca", "Álvaro", "bia"], ["bia", "Zeca", "Álvaro"], ["Álvaro", "bia", "Zeca"],
        ])
        func ties(_ names: [String]) {
            let items = names.enumerated().map { income("\($0.offset)", $0.element, 10) }

            #expect(groups(items).map(\.identity) == [.incomeName("Álvaro"), .incomeName("bia"), .incomeName("Zeca")])
        }

        @Test("names differing only by case still order deterministically", arguments: [["a", "A"], ["A", "a"]])
        func caseTies(_ names: [String]) {
            let items = names.enumerated().map { income("\($0.offset)", $0.element, 10) }

            #expect(groups(items).map(\.identity) == [.incomeName("a"), .incomeName("A")])
        }

        @Test("a failed tags lookup does not degrade income groups")
        func ignoresLookups() {
            let result = groups([income("a", "Salário", 10)], .fixture(failed: [.tags, .categories]))

            #expect(result.map(\.identity) == [.incomeName("Salário")])
        }
    }

    @Suite("Tag")
    struct ByTag {
        private let lookups = LookupSet.fixture(
            categories: [.fixture("casa"), .fixture("lazer")],
            tags: [
                .fixture("luz", "Luz", context: "casa"), .fixture("cinema", "Cinema", context: "lazer"),
                .fixture("agua", "Água", context: "casa"), .fixture("avulsa", "Avulsa"),
                .fixture("z2", "Mesmo"), .fixture("z1", "Mesmo"),
            ]
        )

        private func groups(_ items: [HistoryItem], _ lookups: LookupSet? = nil) -> [LedgerGroupIdentity] {
            LedgerGrouping.groups(of: items, lookups: lookups ?? self.lookups, mode: .tag, kind: .expense).map(\.identity)
        }

        private func rows(_ tags: [String?]) -> [HistoryItem] {
            tags.enumerated().map { .fixture(id: "\($0.offset)", tagIds: $0.element.map { [.of($0)] } ?? []) }
        }

        @Test("tags order by their category's position, then name, then id, with none last")
        func order() {
            let result = groups(rows(["avulsa", nil, "cinema", "luz", "z2", "agua", "z1"]))

            #expect(result == [
                .tag(.of("agua")), .tag(.of("luz")), .tag(.of("cinema")),
                .tag(.of("avulsa")), .tag(.of("z1")), .tag(.of("z2")), .tag(nil),
            ])
        }

        @Test("a row with several tags lands only in its first tag's group")
        func neverDuplicated() {
            #expect(groups([.fixture(tagIds: [.of("luz"), .of("cinema")])]) == [.tag(.of("luz"))])
        }

        @Test("rows sharing a tag share a group")
        func shared() {
            let result = LedgerGrouping.groups(of: rows(["luz", "luz"]), lookups: lookups, mode: .tag, kind: .expense)

            #expect(result.map(\.items.count) == [2])
        }

        @Test("a tag the lookup does not know keeps its id, apart from none and from other unknown tags")
        func unknown() {
            #expect(groups(rows(["gone", nil, "lost"])) == [.tag(.of("gone")), .tag(.of("lost")), .tag(nil)])
        }

        @Test("nil tags inherit nothing yet, so they fall to none")
        func inheritedNothing() {
            #expect(groups([.fixture(tagIds: nil)]) == [.tag(nil)])
        }

        @Test("a failed tags lookup leaves tagged rows unresolved, after untagged ones")
        func degraded() {
            let lookups = LookupSet.fixture(failed: [.tags])

            #expect(groups(rows(["luz", nil]), lookups) == [.tag(nil), .unresolved])
        }

        @Test("a failed categories lookup still resolves tags, ordering them by name")
        func categoriesFailed() {
            let lookups = LookupSet.fixture(tags: [.fixture("luz", "Luz", context: "casa"), .fixture("agua", "Água")], failed: [.categories])

            #expect(groups(rows(["luz", "agua"]), lookups) == [.tag(.of("agua")), .tag(.of("luz"))])
        }
    }
}
