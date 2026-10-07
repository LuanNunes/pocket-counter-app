import Testing

@testable import PocketCounter

@Suite("TransactionRowContent")
struct TransactionRowContentTests {
    private let lookups = LookupSet(
        categories: [],
        tags: [.fixture("a", "Mercado"), .fixture("b", "Casa")],
        cards: [CreditCard(id: CardID(rawValue: "nu"), name: "Cartão Nubank", brand: nil, closingDay: nil, color: nil)]
    )

    private func item(tags: [TagID]? = nil, method: PaymentMethod? = nil, card: String? = nil) -> HistoryItem {
        HistoryItem(
            id: .init(rawValue: "t"), ref: .current, date: .fixture, amount: Money(-10), type: .expense,
            tagIds: tags, paymentMethod: method, cardId: card.map { CardID(rawValue: $0) }, name: "x")
    }

    @Test("the first tag is the chip and the rest are counted")
    func tags() {
        let content = TransactionRowContent.of(item(tags: [.of("a"), .of("b")]), lookups: lookups)
        #expect(content.tag?.name == "Mercado")
        #expect(content.extraTags == 1)
    }

    @Test("an unknown first tag still shows a chip, and the known one after it is counted")
    func unknownFirst() {
        let content = TransactionRowContent.of(item(tags: [.of("gone"), .of("a")]), lookups: lookups)
        #expect(content.tag == .init(name: "Nome indisponível", argb: nil))
        #expect(content.extraTags == 1)
    }

    @Test("a lone unknown tag does not vanish from the row")
    func unknownOnly() {
        let content = TransactionRowContent.of(item(tags: [.of("gone")]), lookups: lookups)
        #expect(content.tag == .init(name: "Nome indisponível", argb: nil))
        #expect(content.extraTags == 0)
    }

    @Test("no tags means no chip and no count")
    func noTags() {
        let content = TransactionRowContent.of(item(), lookups: lookups)
        #expect(content.tag == nil)
        #expect(content.extraTags == 0)
    }

    @Test("credit names the card without its prefix, falling back to Crédito")
    func credit() {
        #expect(TransactionRowContent.of(item(method: .credit, card: "nu"), lookups: lookups).payLabel == "Nubank")
        #expect(TransactionRowContent.of(item(method: .credit, card: "zz"), lookups: lookups).payLabel == "Crédito")
    }

    @Test("other methods use their label and no method uses none")
    func methods() {
        #expect(TransactionRowContent.of(item(method: .pix), lookups: lookups).payLabel == "Pix")
        #expect(TransactionRowContent.of(item(), lookups: lookups).payLabel == nil)
    }
}
