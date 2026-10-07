import Testing

@testable import PocketCounter

@Suite("LedgerGroupLabel")
struct LedgerGroupLabelTests {
    private let lookups = LookupSet(
        categories: [TagContext(id: .of("c"), name: "Casa", color: 0xFF11_2233)],
        tags: [Tag(id: .of("t"), name: "Aluguel", kind: .expense, contextId: .of("c"), color: 0xFF44_5566)],
        cards: []
    )

    @Test("a day, a category and a tag are named with their colour")
    func resolved() {
        #expect(LedgerGroupLabel.of(.day(.of(2026, 5, 19)), lookups: lookups) == .init(name: "19 de maio", argb: nil))
        #expect(LedgerGroupLabel.of(.category(.of("c")), lookups: lookups) == .init(name: "Casa", argb: 0xFF11_2233))
        #expect(LedgerGroupLabel.of(.tag(.of("t")), lookups: lookups) == .init(name: "Aluguel", argb: 0xFF44_5566))
    }

    @Test("the unassigned and unresolved groups have their own names and no colour")
    func unassigned() {
        #expect(LedgerGroupLabel.of(.category(nil), lookups: lookups).name == "Sem categoria")
        #expect(LedgerGroupLabel.of(.tag(nil), lookups: lookups).name == "Sem tag")
        #expect(LedgerGroupLabel.of(.unresolved, lookups: lookups) == .init(name: "Nome indisponível", argb: nil))
    }

    @Test("an id the lookup does not know is unavailable, never none")
    func unknownIds() {
        #expect(LedgerGroupLabel.of(.tag(.of("gone")), lookups: lookups) == .init(name: "Nome indisponível", argb: nil))
        #expect(LedgerGroupLabel.of(.category(.of("gone")), lookups: lookups) == .init(name: "Nome indisponível", argb: nil))
    }
}
