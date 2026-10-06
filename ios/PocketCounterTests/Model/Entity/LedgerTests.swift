import Testing

@testable import PocketCounter

@Suite("LookupSet")
struct LookupSetTests {
    private let casa = TagContext(id: ContextID(rawValue: "c1"), name: "Casa", color: nil)
    private let lazer = TagContext(id: ContextID(rawValue: "c2"), name: "Lazer", color: 0xFF112233)
    private let mercado = Tag(id: TagID(rawValue: "g1"), name: "Mercado", kind: .expense)
    private let nubank = CreditCard(id: CardID(rawValue: "k1"), name: "Nubank", brand: nil, closingDay: 10, color: nil)

    @Test("it keeps category order and indexes everything by id")
    func orderAndIndex() {
        let lookups = LookupSet(categories: [lazer, casa], tags: [mercado], cards: [nubank])

        #expect(lookups.categories == [lazer, casa])
        #expect(lookups.categoriesById[casa.id] == casa)
        #expect(lookups.tagsById[mercado.id] == mercado)
        #expect(lookups.cardsById[nubank.id] == nubank)
        #expect(lookups.tagsById[TagID(rawValue: "zz")] == nil)
        #expect(lookups.failed.isEmpty)
    }

    @Test("it records which lookups failed")
    func failed() {
        let lookups = LookupSet(categories: [], tags: [], cards: [], failed: [.tags, .cards])

        #expect(lookups.failed == [.tags, .cards])
    }
}

@Suite("MonthLedger")
struct MonthLedgerTests {

    @Test("its kpis are computed from its items")
    func kpis() throws {
        let items = [HistoryItem.income(100), .expense(30, status: .pending)]
        let ledger = MonthLedger(
            ref: try RefYearMonth(year: 2026, month: 10), items: items,
            lookups: LookupSet(categories: [], tags: [], cards: [])
        )

        #expect(ledger.kpis == HomeKpis.from(items))
        #expect(ledger.kpis.pendingTotal == Money(30))
    }
}

@Suite("RangeLedger")
struct RangeLedgerTests {

    @Test("every month of the span gets an entry, and empty months are zero")
    func emptyMonthsStayInTheSeries() throws {
        let span = try RefYearMonthRange(
            from: RefYearMonth(year: 2026, month: 1), through: RefYearMonth(year: 2026, month: 12)
        )
        let busy: Set<Int> = [1, 2, 3, 4, 5, 6, 7, 8, 9]
        let items = busy.sorted().map { HistoryItem.fixture(id: "t\($0)", date: .of(2026, $0, 15), amount: 50, type: .income) }
        let ledger = RangeLedger(
            span: span, items: items, lookups: LookupSet(categories: [], tags: [], cards: [])
        )

        let series = ledger.totalsByMonth()

        #expect(series.map(\.0) == span.months)
        #expect(series.count == 12)
        #expect(series.filter { $0.1 == .zero }.map { $0.0.month } == [10, 11, 12])
        #expect(series[0].1.income == Money(50))
    }

    @Test("a month sums only its own items")
    func perMonthTotals() throws {
        let span = try RefYearMonthRange(
            from: RefYearMonth(year: 2026, month: 9), through: RefYearMonth(year: 2026, month: 10)
        )
        let items = [
            HistoryItem.fixture(id: "a", date: .of(2026, 9, 30), amount: 100, type: .income),
            HistoryItem.fixture(id: "b", date: .of(2026, 10, 1), amount: -40, type: .expense),
        ]
        let ledger = RangeLedger(
            span: span, items: items, lookups: LookupSet(categories: [], tags: [], cards: [])
        )

        let series = ledger.totalsByMonth()

        #expect(series[0].1.balance == Money(100))
        #expect(series[1].1.balance == Money(-40))
    }

    @Test("a row whose due date falls past the span stays in its ref month")
    func refMonthWinsOverDueDate() throws {
        let october = try RefYearMonth(year: 2026, month: 10)
        let span = try RefYearMonthRange(from: october, through: october)
        let invoice = HistoryItem.fixture(
            ref: october, date: .of(2026, 11, 10), amount: -300, type: .expense
        )
        let ledger = RangeLedger(
            span: span, items: [invoice], lookups: LookupSet(categories: [], tags: [], cards: [])
        )

        let series = ledger.totalsByMonth()

        #expect(series.map(\.1) == [TransactionTotals.from([invoice])])
        #expect(series[0].1.expense == Money(300))
    }
}
