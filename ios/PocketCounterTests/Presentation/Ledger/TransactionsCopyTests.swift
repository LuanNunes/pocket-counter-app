import Testing

@testable import PocketCounter

@Suite("TransactionsCopy")
struct TransactionsCopyTests {

    @Test("the count pluralises per kind", arguments: [
        (TransactionType.expense, 0, "0 despesas"), (.expense, 1, "1 despesa"), (.expense, 12, "12 despesas"),
        (.income, 0, "0 receitas"), (.income, 1, "1 receita"), (.income, 3, "3 receitas"),
    ])
    func count(kind: TransactionType, count: Int, expected: String) {
        #expect(TransactionsCopy.count(count, kind: kind) == expected)
    }

    @Test("an empty month names its kind")
    func emptyMonth() {
        #expect(TransactionsCopy.emptyMonth(.expense) == "Nenhuma despesa neste mês.")
        #expect(TransactionsCopy.emptyMonth(.income) == "Nenhuma receita neste mês.")
    }
}
