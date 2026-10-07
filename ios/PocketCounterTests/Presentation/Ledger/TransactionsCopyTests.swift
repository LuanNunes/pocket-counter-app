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

    @Test("the status toggle names the current status and the action")
    func statusToggleLabel() {
        #expect(TransactionsCopy.statusToggleLabel(isPaid: true) == "Paga — marcar como pendente")
        #expect(TransactionsCopy.statusToggleLabel(isPaid: false) == "Pendente — marcar como paga")
    }

    @Test("each remedy is titled in pt-BR")
    func remedyTitle() {
        #expect(TransactionsCopy.remedyTitle(.retry) == "Tentar novamente")
        #expect(TransactionsCopy.remedyTitle(.refresh) == "Atualizar")
    }

    @Test("the announcement names the status that was set")
    func statusMarked() {
        #expect(TransactionsCopy.statusMarked(.paid) == "Marcada como paga")
        #expect(TransactionsCopy.statusMarked(.pending) == "Marcada como pendente")
    }

    @Test("a missing or empty detail value reads as a dash", arguments: [(nil as String?, "—"), ("", "—"), ("Pix", "Pix")])
    func detailValue(text: String?, expected: String) {
        #expect(TransactionsCopy.detailValue(text) == expected)
    }

    @Test("categories join by name, and none reads as a dash")
    func detailCategories() {
        let tags = [
            TransactionRowContent.TagChip(name: "Mercado", argb: nil),
            TransactionRowContent.TagChip(name: "Restaurante", argb: nil),
        ]
        #expect(TransactionsCopy.detailCategories(tags) == "Mercado, Restaurante")
        #expect(TransactionsCopy.detailCategories([]) == "—")
    }
}
