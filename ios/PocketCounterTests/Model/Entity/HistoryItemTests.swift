import Testing

@testable import PocketCounter

@Suite("HistoryItem")
struct HistoryItemTests {

    @Test("the title prefers name, then description, then a dash", arguments: [
        ("Mercado", "Compras", "Mercado"),
        (nil, "Compras", "Compras"),
        ("  ", "Compras", "Compras"),
        ("", "", "—"),
        (nil, nil, "—"),
    ] as [(String?, String?, String)])
    func displayTitle(name: String?, description: String?, expected: String) {
        let item = HistoryItem.fixture(name: name, description: description)

        #expect(item.displayTitle() == expected)
    }

    @Test("an item is fixo exactly when it belongs to a series")
    func isFixo() {
        #expect(HistoryItem.fixture(seriesId: "s1").isFixo)
        #expect(!HistoryItem.fixture(seriesId: nil).isFixo)
    }

    @Test("no own tags and an empty override are different things")
    func tagIdsNilIsNotEmpty() {
        let inherits = HistoryItem.fixture(tagIds: nil)
        let overridesWithNone = HistoryItem.fixture(tagIds: [])

        #expect(inherits != overridesWithNone)
    }

    @Test("a new item defaults to paid, unordered and not an invoice")
    func defaults() {
        let item = HistoryItem.fixture()

        #expect(item.statusPayment == .paid)
        #expect(item.displayOrder == 0)
        #expect(!item.isInvoice)
        #expect(item.paymentMethod == nil)
        #expect(item.cardId == nil)
    }
}

@Suite("Tag, TagContext and CreditCard")
struct CatalogEntityTests {

    @Test("a tag carries its kind and optional context, colour and series")
    func tag() {
        let tag = Tag(id: TagID(rawValue: "g"), name: "Mercado", kind: .expense)

        #expect(tag.contextId == nil)
        #expect(tag.color == nil)
        #expect(tag.seriesId == nil)
        #expect(tag != Tag(id: TagID(rawValue: "g"), name: "Mercado", kind: .income))
    }

    @Test("a context has a name and a colour")
    func context() {
        let context = TagContext(id: ContextID(rawValue: "c"), name: "Casa", color: 0xFF112233)

        #expect(context.color == 0xFF112233)
    }

    @Test("a card keeps its limit as money")
    func card() {
        let card = CreditCard(id: CardID(rawValue: "k"), name: "Nubank", brand: "mastercard", last4: "1234", limit: Money(5000), billDay: 10)

        #expect(card.limit == Money(5000))
    }
}
