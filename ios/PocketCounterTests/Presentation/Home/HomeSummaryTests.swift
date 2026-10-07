import Testing

@testable import PocketCounter

@Suite("HomeSummary")
struct HomeSummaryTests {
    private let card = CreditCard(id: CardID(rawValue: "k1"), name: "Nubank", brand: nil, closingDay: 10, color: nil)

    private func ledger(_ items: [HistoryItem], failed: Set<LookupKind> = []) throws -> MonthLedger {
        MonthLedger(
            ref: try #require(RefYearMonth(raw: 202610)),
            items: items,
            lookups: LookupSet(categories: [], tags: [], cards: failed.contains(.cards) ? [] : [card], failed: failed)
        )
    }

    private var items: [HistoryItem] {
        [.income(1000), .expense(200), .expense(50, status: .pending), .invoice(300, card: card.id)]
    }

    @Test("it carries every figure Início shows")
    func figures() throws {
        let ledger = try ledger(items)

        let summary = HomeSummary.from(ledger)

        #expect(summary.kpis == ledger.kpis)
        #expect(summary.openInvoices == ledger.openInvoices)
        #expect(summary.openInvoices.total == Money(300))
        #expect(summary.openInvoices.cardCount == 1)
        #expect(summary.transactionCount == 4)
    }

    @Test("pending shows only when something is pending")
    func showsPending() throws {
        #expect(HomeSummary.from(try ledger(items)).showsPending)
        #expect(!HomeSummary.from(try ledger([.income(10), .expense(5)])).showsPending)
    }

    @Test("failed cards blank the invoice figures and nothing else")
    func cardsFailed() throws {
        let summary = HomeSummary.from(try ledger(items, failed: [.cards]))

        #expect(summary.openInvoices.total == nil)
        #expect(summary.openInvoices.cardCount == nil)
        #expect(summary.kpis.totals.balance == Money(450))
        #expect(summary.kpis.totals.income == Money(1000))
        #expect(summary.kpis.totals.expense == Money(550))
    }
}
