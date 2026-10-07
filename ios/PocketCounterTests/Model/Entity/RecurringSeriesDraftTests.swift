import Testing

@testable import PocketCounter

@Suite("RecurringSeriesDraft")
struct RecurringSeriesDraftTests {
    @Test("the name, type and day of the row are the series'", arguments: [
        (HistoryItem.fixture(date: .of(2026, 10, 5), type: .expense, name: "Aluguel"),
         RecurringSeriesDraft(name: "Aluguel", type: .expense, recurrenceDay: 5)),
        (.fixture(date: .of(2026, 10, 28), type: .income, name: "Salário"),
         RecurringSeriesDraft(name: "Salário", type: .income, recurrenceDay: 28)),
    ])
    func fromRow(item: HistoryItem, expected: RecurringSeriesDraft) {
        #expect(RecurringSeriesDraft.makingFixo(item) == expected)
    }

    @Test("a blank name falls back to the description, then to a generic name", arguments: [
        (HistoryItem.fixture(name: "  ", description: "Condomínio"), "Condomínio"),
        (.fixture(name: nil, description: "Internet"), "Internet"),
        (.fixture(name: "", description: " "), "Conta fixa"),
        (.fixture(name: nil, description: nil), "Conta fixa"),
    ])
    func nameFallback(item: HistoryItem, expected: String) {
        #expect(RecurringSeriesDraft.makingFixo(item).name == expected)
    }

    @Test("two rows with the same name and type draft the same series: the name is a grouping key")
    func collision() {
        let a = HistoryItem.fixture(id: "a", date: .of(2026, 10, 3), name: nil)
        let b = HistoryItem.fixture(id: "b", date: .of(2026, 10, 3), name: nil)

        #expect(RecurringSeriesDraft.makingFixo(a) == RecurringSeriesDraft.makingFixo(b))
        #expect(RecurringSeriesDraft.makingFixo(a).name == "Conta fixa")
    }

    @Test("the same name under another type is another series")
    func typeSeparates() {
        let expense = HistoryItem.fixture(type: .expense, name: "Extra")
        let income = HistoryItem.fixture(type: .income, name: "Extra")

        #expect(RecurringSeriesDraft.makingFixo(expense) != RecurringSeriesDraft.makingFixo(income))
    }
}
