import Foundation
import Testing

@testable import PocketCounter

@Suite("QuickAddReviewRows")
struct QuickAddReviewRowsTests {
    private let today = CalendarDay.of(2026, 10, 7)
    private let card = CreditCard(id: CardID(rawValue: "k1"), name: "Nubank", brand: nil, closingDay: nil, color: nil)

    private func rows(
        _ draft: ReadingDraft, lookups: LookupSet = .fixture()
    ) -> [QuickAddReviewRow] {
        QuickAddReviewRows.rows(from: draft, lookups: lookups, today: today)
    }

    private func row(_ field: QuickAddReviewRow.Field, _ rows: [QuickAddReviewRow]) throws -> QuickAddReviewRow {
        try #require(rows.first { $0.field == field })
    }

    @Test("today and yesterday are named, any other day is not")
    func dateValue() throws {
        let draft = ReadingDraft(.fixture(date: Sourced(value: today, source: .inferred)))

        #expect(try row(.date, rows(draft)).value == "07/10 · hoje")
        #expect(try row(.date, rows(draft.settingDate(.of(2026, 10, 6)))).value == "06/10 · ontem")
        #expect(try row(.date, rows(draft.settingDate(.of(2026, 10, 1)))).value == "01/10")
    }

    @Test("an inferred date is assumed; correcting it makes it defined")
    func dateBadge() throws {
        let draft = ReadingDraft(.fixture(date: Sourced(value: today, source: .inferred)))

        #expect(try row(.date, rows(draft)).provenance == .assumed)
        #expect(try row(.date, rows(draft.settingDate(today))).provenance == .defined)
    }

    @Test("a card shows its name, not the method")
    func cardValue() throws {
        let draft = ReadingDraft(.fixture()).settingCard(.fixture("k1", "Nubank"))

        #expect(try row(.paymentMethod, rows(draft, lookups: .fixture(cards: [card]))).value == "Nubank")
    }

    @Test("a field the sentence did not bring is weak and wears no badge")
    func weakFields() throws {
        let rows = rows(ReadingDraft(.fixture()))

        #expect(try row(.paymentMethod, rows).isWeak)
        #expect(try row(.paymentMethod, rows).provenance == nil)
        #expect(try row(.tag, rows).value == "sem categoria")
    }

    @Test("the date is spoken as a day name, never as digits")
    func spokenDate() throws {
        let draft = ReadingDraft(.fixture(date: Sourced(value: today, source: .inferred)))

        #expect(try row(.date, rows(draft)).spokenValue == "7 de outubro, hoje")
        #expect(try row(.date, rows(draft.settingDate(.of(2026, 10, 6)))).spokenValue == "6 de outubro, ontem")
        #expect(try row(.date, rows(draft.settingDate(.of(2026, 10, 1)))).spokenValue == "1 de outubro")
    }

    @Test("a row that is not a date is spoken as it is written")
    func spokenOthers() throws {
        let draft = ReadingDraft(.fixture()).settingCard(.fixture("k1", "Nubank"))
        let rows = rows(draft, lookups: .fixture(cards: [card]))

        #expect(try row(.paymentMethod, rows).spokenValue == "Nubank")
        #expect(try row(.tag, rows).spokenValue == "sem categoria")
    }

    @Test("the payment row is named as the design names it")
    func paymentLabel() throws {
        #expect(try row(.paymentMethod, rows(ReadingDraft(.fixture()))).label == "Forma de Pagamento")
    }

    @Test("a card chip drops the redundant word before the name")
    func cardChipLabel() {
        let named = CreditCard(id: CardID(rawValue: "k2"), name: "Cartão Itaú", brand: nil, closingDay: nil, color: nil)
        let chips = QuickAddReviewRows.chips(
            for: .paymentMethod, from: ReadingDraft(.fixture()), lookups: .fixture(cards: [named]), today: today
        )

        #expect(chips.last?.label == "Itaú")
    }

    @Test("what was understood lists the amount, kind, name, date and method in that order")
    func understood() {
        let draft = ReadingDraft(.fixture(
            date: Sourced(value: today, source: .inferred), paymentMethod: Sourced(value: .pix, source: .written)
        ))

        let items = QuickAddReviewRows.understood(from: draft)

        #expect(items.map(\.id) == ["amount", "type", "name", "date", "method"])
        #expect(items.map(\.text) == ["R$\u{A0}250,00", "Despesa", "Consulta do cachorro", "07/10", "Pix"])
        #expect(items.first { $0.id == "date" }?.spoken == "7 de outubro")
    }

    @Test("a field the server has not read yet is left out")
    func understoodOmitsMissing() {
        let draft = ReadingDraft(.fixture(type: nil, amount: nil, name: nil))

        #expect(QuickAddReviewRows.understood(from: draft).map(\.id) == ["date"])
    }

    @Test("the date chips are today and the two days before it")
    func dateChips() {
        let chips = QuickAddReviewRows.chips(
            for: .date, from: ReadingDraft(.fixture()), lookups: .fixture(), today: today
        )

        #expect(chips.map(\.label) == ["Hoje", "Ontem", "Anteontem"])
        #expect(chips.map(\.change) == [
            .date(.of(2026, 10, 7)), .date(.of(2026, 10, 6)), .date(.of(2026, 10, 5)),
        ])
    }

    @Test("the category chips are the tags of the draft's own kind")
    func tagChips() {
        let expense = PocketCounter.Tag.fixture("g1", "Mercado", kind: .expense)
        let income = PocketCounter.Tag.fixture("g2", "Salário", kind: .income)

        let chips = QuickAddReviewRows.chips(
            for: .tag, from: ReadingDraft(.fixture()), lookups: .fixture(tags: [expense, income]),
            today: today
        )

        #expect(chips.map(\.label) == ["Mercado"])
    }

    /// A failed lookup must not read as "you have no cards": the methods stay, the cards go.
    @Test("cards that failed to load drop their chips and keep the methods")
    func cardChipsDegraded() {
        let chips = QuickAddReviewRows.chips(
            for: .paymentMethod, from: ReadingDraft(.fixture()),
            lookups: .fixture(cards: [card], failed: [.cards]), today: today
        )

        #expect(chips.map(\.label) == ["Pix", "Débito", "Dinheiro"])
    }

    @Test("a degraded lookup says so instead of offering nothing")
    func degraded() throws {
        let rows = rows(ReadingDraft(.fixture()), lookups: .fixture(failed: [.cards, .tags]))

        #expect(try row(.paymentMethod, rows).unavailable == QuickAddCopy.cardsUnavailable)
        #expect(try row(.tag, rows).unavailable == QuickAddCopy.tagsUnavailable)
        #expect(try row(.date, rows).unavailable == nil)
    }
}
