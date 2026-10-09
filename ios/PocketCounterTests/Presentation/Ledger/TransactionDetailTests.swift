import Testing

@testable import PocketCounter

private let october = RefYearMonth(raw: 202610)!
private let november = RefYearMonth(raw: 202611)!

@MainActor
@Suite("TransactionDetailProjection")
struct TransactionDetailTests {
    private let lookups = LookupSet(
        categories: [],
        tags: [.fixture("a", "Mercado"), .fixture("b", "Casa")],
        cards: [CreditCard(id: CardID(rawValue: "nu"), name: "Cartão Nubank", brand: nil, closingDay: nil, color: nil)]
    )
    private let rent = HistoryItem(
        id: TransactionID(rawValue: "rent"), ref: october, date: .of(2026, 10, 5), amount: Money(-1500),
        type: .expense, tagIds: [.of("a"), .of("b")], statusPayment: .pending, paymentMethod: .credit,
        cardId: CardID(rawValue: "nu"), name: "Aluguel")
    private let target = DetailTarget(id: TransactionID(rawValue: "rent"), ref: october)

    private func loadedState(
        _ items: [HistoryItem]? = nil, month: RefYearMonth = october
    ) async -> MonthLedgerModel.State {
        let source = LedgerSourceFake()
        source.ledgers = [october: MonthLedger(ref: october, items: items ?? [rent], lookups: lookups)]
        let model = LedgerModelFixture.model(
            window: .around(october), month: october, loadMonth: source.action, onSessionExpired: {})
        await model.load()
        var state = model.state
        state.month = month
        return state
    }

    private func detail(_ projection: TransactionDetailProjection) -> TransactionDetail? {
        guard case .showing(let detail) = projection else { return nil }
        return detail
    }

    @Test("a row in the month on screen is shown with its facts resolved")
    func showing() async throws {
        let detail = try #require(detail(.of(target, in: await loadedState())))

        #expect(detail.item == rent)
        #expect(detail.title == "Aluguel")
        #expect(detail.dateLabel == "5 de outubro")
        #expect(detail.kind == .expense)
        #expect(detail.amount == -1500)
        #expect(detail.payLabel == "Nubank")
        #expect(detail.tags.map(\.name) == ["Mercado", "Casa"])
        #expect(!detail.isPaid)
        #expect(!detail.isFixo)
    }

    @Test("a tag the lookups cannot name still shows, as unavailable")
    func unknownTag() async throws {
        let ghost = HistoryItem.fixture(id: "rent", tagIds: [.of("gone")])

        let detail = try #require(detail(.of(target, in: await loadedState([ghost]))))

        #expect(detail.tags.map(\.name) == ["Nome indisponível"])
    }

    @Test("a fresh answer with other tags is what the detail shows next")
    func rederives() async throws {
        var state = await loadedState()
        let retagged = HistoryItem.fixture(id: "rent", tagIds: [.of("b")], recurringTransactionId: "s1", name: "Aluguel")

        state.commit(MonthLedger(ref: october, items: [retagged], lookups: lookups), for: october, at: state.writes.revision)

        let detail = try #require(detail(.of(target, in: state)))
        #expect(detail.tags.map(\.name) == ["Casa"])
        #expect(detail.isFixo)
    }

    @Test("a row removed from the ledger is gone")
    func removed() async {
        var state = await loadedState()

        state.completeDeletion(rent.id, ref: october)

        #expect(TransactionDetailProjection.of(target, in: state) == .gone)
    }

    @Test("a row the ledger never held is gone")
    func absent() async {
        #expect(TransactionDetailProjection.of(target, in: await loadedState([])) == .gone)
    }

    @Test("another month on screen makes the detail gone, whichever tab moved it")
    func otherMonth() async {
        #expect(TransactionDetailProjection.of(target, in: await loadedState(month: november)) == .gone)
    }

    @Test("the Paga switch follows the status overlay")
    func overlay() async throws {
        var state = await loadedState()
        state.beginStatus(rent.id, ref: october, target: .paid)

        let detail = try #require(detail(.of(target, in: state)))

        #expect(detail.isPaid)
        #expect(detail.item.statusPayment == .paid)
    }

    @Test("the fixo switch shows what was asked for while the ledger still shows what the server confirmed", arguments: [
        (RowIntent.fixo(true), true), (.fixo(false), false),
    ])
    func fixoIntent(intent: RowIntent, expected: Bool) async throws {
        var state = await loadedState()
        state.beginIntent(rent.id, ref: october, target: intent)

        let detail = try #require(detail(.of(target, in: state)))

        #expect(detail.isFixo == expected)
        #expect(detail.item.isFixo == false)
    }

    @Test("a failed fixo toggle snaps the switch back to the committed value")
    func failedFixo() async throws {
        var state = await loadedState()
        state.beginIntent(rent.id, ref: october, target: .fixo(true))
        state.failIntent(rent.id, ref: october, .unreachable)

        let detail = try #require(detail(.of(target, in: state)))

        #expect(!detail.isFixo)
        #expect(detail.fixoWrite.notice?.title == "Não foi possível salvar")
        #expect(detail.deleteWrite == .none)
    }

    @Test("a deletion in flight is busy and leaves the fixo switch on the committed value")
    func deleting() async throws {
        var state = await loadedState([HistoryItem.fixture(id: "rent", recurringTransactionId: "s1")])
        state.beginIntent(rent.id, ref: october, target: .deletion)

        let detail = try #require(detail(.of(target, in: state)))

        #expect(detail.deleteWrite.isBusy)
        #expect(detail.fixoWrite == .none)
        #expect(detail.isBusy)
        #expect(detail.isFixo)
    }

    @Test("a failed deletion is titled by deleting")
    func deleteFailed() async throws {
        var state = await loadedState()
        state.beginIntent(rent.id, ref: october, target: .deletion)
        state.failIntent(rent.id, ref: october, .server)

        let detail = try #require(detail(.of(target, in: state)))

        #expect(detail.deleteWrite.notice?.title == "Não foi possível excluir")
        #expect(detail.deleteWrite.remedy == .retry)
        #expect(detail.fixoWrite == .none)
    }

    @Test("a status write that failed shows its notice")
    func statusFailed() async throws {
        var state = await loadedState()
        state.beginStatus(rent.id, ref: october, target: .paid)
        state.failStatus(rent.id, ref: october, .server)

        let detail = try #require(detail(.of(target, in: state)))

        #expect(detail.statusWrite.notice?.title == "Não foi possível salvar")
        #expect(!detail.isPaid)
    }

    @Test("a row filtered out of the list is still shown: a filter is a lens, not a removal")
    func survivesFilters() async {
        var view = TransactionsViewState()
        view.openDetail(target)
        view.select(kind: .income)
        view.select(mode: .tag)
        view.set(query: "nada")

        #expect(view.detail == target)
        #expect(detail(.of(target, in: await loadedState())) != nil)
    }
}
