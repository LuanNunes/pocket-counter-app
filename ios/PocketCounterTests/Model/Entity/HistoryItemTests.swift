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

    @Test("an item is fixo exactly when it belongs to a recurring transaction")
    func isFixo() {
        #expect(HistoryItem.fixture(recurringTransactionId: "r1").isFixo)
        #expect(!HistoryItem.fixture(recurringTransactionId: nil).isFixo)
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

    @Test("a tag carries its kind and optional context and colour")
    func tag() {
        let tag = Tag(id: TagID(rawValue: "g"), name: "Mercado", kind: .expense)

        #expect(tag.contextId == nil)
        #expect(tag.color == nil)
        #expect(tag != Tag(id: TagID(rawValue: "g"), name: "Mercado", kind: .income))
    }

    @Test("a context may have no colour")
    func context() {
        let context = TagContext(id: ContextID(rawValue: "c"), name: "Casa", color: nil)

        #expect(context.color == nil)
    }

    @Test("a card carries only what the backend sends")
    func card() {
        let card = CreditCard(id: CardID(rawValue: "k"), name: "Nubank", brand: nil, closingDay: nil, color: nil)

        #expect(card.brand == nil)
        #expect(card.closingDay == nil)
        #expect(card.color == nil)
    }

    @Test("setting the payment status changes that field and nothing else")
    func settingPaymentStatus() {
        let item = HistoryItem.fixture(
            id: "t9", amount: 42, tagIds: [.of("g1")], statusPayment: .paid, displayOrder: 3,
            recurringTransactionId: "r1", name: "Aluguel", isInvoice: true
        )

        let pending = item.settingPaymentStatus(.pending)

        #expect(pending == HistoryItem.fixture(
            id: "t9", amount: 42, tagIds: [.of("g1")], statusPayment: .pending, displayOrder: 3,
            recurringTransactionId: "r1", name: "Aluguel", isInvoice: true
        ))
    }

    @Test("setting the display order changes that field and nothing else")
    func settingDisplayOrder() {
        let item = HistoryItem.fixture(
            id: "t9", amount: 42, tagIds: [.of("g1")], statusPayment: .pending, displayOrder: 3,
            recurringTransactionId: "r1", name: "Aluguel", isInvoice: true
        )

        let moved = item.settingDisplayOrder(0)

        #expect(moved == HistoryItem.fixture(
            id: "t9", amount: 42, tagIds: [.of("g1")], statusPayment: .pending, displayOrder: 0,
            recurringTransactionId: "r1", name: "Aluguel", isInvoice: true
        ))
    }
}
