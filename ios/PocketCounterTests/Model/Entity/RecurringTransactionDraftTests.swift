import Testing

@testable import PocketCounter

@Suite("RecurringTransactionDraft")
struct RecurringTransactionDraftTests {
    @Test("the name and type of the row are the recurring transaction's", arguments: [
        (HistoryItem.fixture(date: .of(2026, 10, 5), type: .expense, name: "Aluguel"),
         RecurringTransactionDraft(name: "Aluguel", type: .expense)),
        (.fixture(date: .of(2026, 10, 28), type: .income, name: "Salário"),
         RecurringTransactionDraft(name: "Salário", type: .income)),
    ])
    func fromRow(item: HistoryItem, expected: RecurringTransactionDraft) {
        #expect(RecurringTransactionDraft.makingFixo(item) == expected)
    }

    @Test("a blank name falls back to the description, then to a generic name", arguments: [
        (HistoryItem.fixture(name: "  ", description: "Condomínio"), "Condomínio"),
        (.fixture(name: nil, description: "Internet"), "Internet"),
        (.fixture(name: "", description: " "), "Conta fixa"),
        (.fixture(name: nil, description: nil), "Conta fixa"),
    ])
    func nameFallback(item: HistoryItem, expected: String) {
        #expect(RecurringTransactionDraft.makingFixo(item).name == expected)
    }

    @Test("two rows with the same name and type draft the same recurring transaction: the name is a grouping key")
    func collision() {
        let a = HistoryItem.fixture(id: "a", date: .of(2026, 10, 3), name: nil)
        let b = HistoryItem.fixture(id: "b", date: .of(2026, 10, 3), name: nil)

        #expect(RecurringTransactionDraft.makingFixo(a) == RecurringTransactionDraft.makingFixo(b))
        #expect(RecurringTransactionDraft.makingFixo(a).name == "Conta fixa")
    }

    @Test("the same name under another type is another recurring transaction")
    func typeSeparates() {
        let expense = HistoryItem.fixture(type: .expense, name: "Extra")
        let income = HistoryItem.fixture(type: .income, name: "Extra")

        #expect(RecurringTransactionDraft.makingFixo(expense) != RecurringTransactionDraft.makingFixo(income))
    }
}
