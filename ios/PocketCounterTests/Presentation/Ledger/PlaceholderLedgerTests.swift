import Testing

@testable import PocketCounter

@Suite("PlaceholderLedger")
struct PlaceholderLedgerTests {

    private func ledger() throws -> MonthLedger {
        .placeholder(for: try #require(RefYearMonth(raw: 202610)))
    }

    @Test("it belongs to the month asked for")
    func ref() throws {
        let ledger = try ledger()
        #expect(ledger.ref.raw == 202610)
    }

    @Test("it has both incomes and expenses, so every row shape is redacted")
    func bothTypes() throws {
        let ledger = try ledger()
        #expect(ledger.items.contains { $0.type == .income })
        #expect(ledger.items.contains { $0.type == .expense })
    }

    @Test("it has a pending expense, so the pending figure has a width")
    func pending() throws {
        let ledger = try ledger()
        #expect(ledger.kpis.pendingCount > 0)
    }

    @Test("it has lookups and none of them failed, or a skeleton would render the degraded card")
    func lookups() throws {
        let ledger = try ledger()
        #expect(!ledger.lookups.categories.isEmpty)
        #expect(!ledger.lookups.tags.isEmpty)
        #expect(!ledger.lookups.cards.isEmpty)
        #expect(ledger.lookups.failed.isEmpty)
    }

    @Test("every item belongs to the placeholder's month")
    func itemsInMonth() throws {
        let ledger = try ledger()
        #expect(ledger.items.allSatisfy { $0.ref == ledger.ref })
    }

    @Test("it has an invoice, so the Faturas tile redacts a real-width figure")
    func invoice() throws {
        let openInvoices = try ledger().openInvoices

        let total = try #require(openInvoices.total)
        #expect(total > .zero)
        #expect(openInvoices.cardCount != nil)
    }
}
