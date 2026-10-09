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
