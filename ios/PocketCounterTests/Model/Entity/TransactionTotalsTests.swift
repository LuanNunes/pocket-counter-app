import Testing

@testable import PocketCounter

@Suite("TransactionTotals")
struct TransactionTotalsTests {

    @Test("no items total zero")
    func empty() {
        #expect(TransactionTotals.from([]) == .zero)
    }

    @Test("income sums its amounts directly")
    func income() {
        let totals = TransactionTotals.from([.income(100), .income(50)])

        #expect(totals == TransactionTotals(income: Money(150), expense: .zero, balance: Money(150)))
    }

    @Test("expense is the absolute value of its negative amounts, so balance goes negative")
    func expense() {
        let totals = TransactionTotals.from([.expense(100), .expense(30)])

        #expect(totals == TransactionTotals(income: .zero, expense: Money(130), balance: Money(-130)))
    }

    @Test("balance is income minus expense")
    func balance() {
        let totals = TransactionTotals.from([.income(1000), .expense(250)])

        #expect(totals.balance == Money(750))
        #expect(totals.expense == Money(250))
    }

    @Test("an expense stored with a positive sign still counts as a positive magnitude")
    func expenseSignAgnostic() {
        let positiveExpense = HistoryItem.fixture(amount: 40, type: .expense)

        #expect(TransactionTotals.from([positiveExpense]).expense == Money(40))
    }

    @Test("pending items are counted like paid ones")
    func pendingCounts() {
        let totals = TransactionTotals.from([.income(100, status: .pending), .expense(40, status: .pending)])

        #expect(totals.balance == Money(60))
    }
}

@Suite("HomeKpis")
struct HomeKpisTests {

    @Test("no items give zeroed kpis")
    func empty() {
        #expect(HomeKpis.from([]) == HomeKpis(totals: .zero, expenseCount: 0, incomeCount: 0, pendingTotal: .zero, pendingCount: 0))
    }

    @Test("totals delegate to TransactionTotals and counts split by type")
    func totalsAndCounts() {
        let items: [HistoryItem] = [.income(100), .expense(20), .expense(5)]

        let kpis = HomeKpis.from(items)

        #expect(kpis.totals == TransactionTotals.from(items))
        #expect(kpis.incomeCount == 1)
        #expect(kpis.expenseCount == 2)
    }

    @Test("pending is only pending expenses, as a positive amount")
    func pendingExpensesOnly() {
        let kpis = HomeKpis.from([
            .expense(40, status: .pending),
            .expense(10, status: .pending),
            .expense(99, status: .paid),
        ])

        #expect(kpis.pendingTotal == Money(50))
        #expect(kpis.pendingCount == 2)
    }

    @Test("pending income is receivable, not owed, so it is excluded")
    func pendingIncomeExcluded() {
        let kpis = HomeKpis.from([.income(500, status: .pending)])

        #expect(kpis.pendingTotal == .zero)
        #expect(kpis.pendingCount == 0)
        #expect(kpis.incomeCount == 1)
    }
}
