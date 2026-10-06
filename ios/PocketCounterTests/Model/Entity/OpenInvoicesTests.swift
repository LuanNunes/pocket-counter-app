import Testing

@testable import PocketCounter

@Suite("OpenInvoices")
struct OpenInvoicesTests {
    private let nubank = CreditCard(id: CardID(rawValue: "k1"), name: "Nubank", brand: nil, closingDay: 10, color: nil)
    private let inter = CreditCard(id: CardID(rawValue: "k2"), name: "Inter", brand: nil, closingDay: 5, color: nil)

    private func openInvoices(_ items: [HistoryItem], cards: [CreditCard], failed: Set<LookupKind> = []) -> OpenInvoices {
        .from(items, lookups: LookupSet(categories: [], tags: [], cards: cards, failed: failed))
    }

    @Test("a paid invoice still counts: the header is the source of truth")
    func paidInvoiceCounts() {
        let result = openInvoices([.invoice(300, status: .paid, card: nubank.id)], cards: [nubank])

        #expect(result.total == Money(300))
    }

    @Test("a paid invoice is the whole total: its card's own purchases are not added on top")
    func paidInvoiceWinsOverTheFallback() {
        let items = [
            HistoryItem.invoice(300, status: .paid, card: nubank.id),
            .fixture(amount: -80, type: .expense, cardId: nubank.id),
        ]

        #expect(openInvoices(items, cards: [nubank]).total == Money(300))
    }

    @Test("a card with no invoice row sums its own expenses")
    func fallbackToCardExpenses() {
        let items = [
            HistoryItem.fixture(amount: -40, type: .expense, cardId: nubank.id),
            .fixture(amount: -60, type: .expense, cardId: nubank.id),
            .fixture(amount: -999, type: .expense, cardId: inter.id),
        ]

        let result = openInvoices(items, cards: [nubank])

        #expect(result.total == Money(100))
    }

    @Test("an income carrying a card id is not part of the invoice")
    func incomeIsNotCounted() {
        let items = [
            HistoryItem.fixture(amount: -100, type: .expense, cardId: nubank.id),
            .fixture(amount: 30, type: .income, cardId: nubank.id),
        ]

        #expect(openInvoices(items, cards: [nubank]).total == Money(100))
    }

    @Test("an income flagged as an invoice does not stand in for the invoice row")
    func incomeInvoiceFlagIgnored() {
        let items = [
            HistoryItem.fixture(amount: -100, type: .expense, cardId: nubank.id),
            .fixture(amount: 30, type: .income, isInvoice: true, cardId: nubank.id),
        ]

        #expect(openInvoices(items, cards: [nubank]).total == Money(100))
    }

    @Test("a plain expense on a card that has an invoice row is not counted twice")
    func noDoubleCount() {
        let items = [
            HistoryItem.invoice(300, card: nubank.id),
            .fixture(amount: -50, type: .expense, statusPayment: .pending, cardId: nubank.id),
        ]

        let result = openInvoices(items, cards: [nubank])

        #expect(result.total == Money(300))
    }

    @Test("the total is a positive magnitude summed across cards")
    func positiveAcrossCards() {
        let items = [HistoryItem.invoice(300, card: nubank.id), .invoice(120.50, card: inter.id)]

        let result = openInvoices(items, cards: [nubank, inter])

        #expect(result.total == Money(420.50))
        #expect(result.cardCount == 2)
    }

    @Test("an invoice row of a card that is not in the lookups is ignored")
    func unknownCard() {
        let result = openInvoices([.invoice(300, card: inter.id)], cards: [nubank])

        #expect(result.total == .zero)
    }

    @Test("no cards loaded is a zero total and a zero count")
    func noCards() {
        let result = openInvoices([.invoice(300, card: nubank.id)], cards: [])

        #expect(result.total == .zero)
        #expect(result.cardCount == 0)
    }

    @Test("a failed cards lookup makes both figures unknown")
    func cardsFailed() {
        let result = openInvoices([.invoice(300, card: nubank.id)], cards: [], failed: [.cards])

        #expect(result.total == nil)
        #expect(result.cardCount == nil)
    }

    @Test("a failed lookup other than cards leaves both figures known")
    func otherFailure() {
        let result = openInvoices([.invoice(300, card: nubank.id)], cards: [nubank], failed: [.tags])

        #expect(result.total == Money(300))
        #expect(result.cardCount == 1)
    }
}
