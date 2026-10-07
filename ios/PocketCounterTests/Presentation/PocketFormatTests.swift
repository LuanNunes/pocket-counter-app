import Foundation
import Testing

@testable import PocketCounter

@Suite("PocketFormat")
struct PocketFormatTests {

    // MARK: Currency

    @Test("money is grouped and decimalised the pt-BR way")
    func currencyUsesBrazilianSeparators() {
        let formatted = PocketFormat.currency(Decimal(string: "1234.56")!)

        #expect(formatted.hasPrefix("R$"))
        #expect(formatted.contains("1.234,56"))
    }

    @Test("the locale is pinned, not inherited from the device")
    func currencyIgnoresDeviceLocale() {
        // A user whose phone is in English must still see R$, not $.
        #expect(PocketFormat.currency(Decimal(string: "9.90")!).hasPrefix("R$"))
    }

    @Test("cents are always shown, even when round")
    func currencyAlwaysShowsCents() {
        #expect(PocketFormat.currency(Decimal(42)).contains("42,00"))
    }

    @Test("a negative amount uses the true minus sign, attached to the symbol")
    func currencyKeepsNegativeSign() {
        let formatted = PocketFormat.currency(Decimal(string: "-12.30")!)

        #expect(formatted.contains("\u{2212}"))
        #expect(formatted.contains("-") == false)
        #expect(formatted.hasPrefix("\u{2212}R$") || formatted.hasPrefix("R$\u{2212}"))
    }

    @Test("an amount can be rendered without its sign, for a column that labels it instead")
    func currencyCanDropTheSign() {
        let formatted = PocketFormat.currency(Decimal(string: "-12.30")!, signed: false)

        #expect(formatted.contains("-") == false)
        #expect(formatted.contains("\u{2212}") == false)
        #expect(formatted.contains("12,30"))
    }

    // MARK: Month labels

    @Test("month names are pt-BR and lowercase", arguments: [
        (1, "janeiro"), (2, "fevereiro"), (3, "março"), (4, "abril"),
        (5, "maio"), (6, "junho"), (7, "julho"), (8, "agosto"),
        (9, "setembro"), (10, "outubro"), (11, "novembro"), (12, "dezembro"),
    ])
    func monthLabelIsLowercasePtBR(month: Int, expected: String) {
        #expect(PocketFormat.monthLabel(year: 2026, month: month) == expected)
    }

    @Test("a month outside the current year carries the year")
    func monthLabelWithYear() {
        #expect(PocketFormat.monthLabel(year: 2025, month: 3, showingYear: true) == "março de 2025")
    }

    @Test("a standalone month name is pt-BR, and capitalised on request", arguments: [
        (1, "janeiro", "Janeiro"), (2, "fevereiro", "Fevereiro"), (3, "março", "Março"),
        (4, "abril", "Abril"), (5, "maio", "Maio"), (6, "junho", "Junho"),
        (7, "julho", "Julho"), (8, "agosto", "Agosto"), (9, "setembro", "Setembro"),
        (10, "outubro", "Outubro"), (11, "novembro", "Novembro"), (12, "dezembro", "Dezembro"),
    ])
    func monthName(month: Int, plain: String, capitalized: String) throws {
        let ref = try RefYearMonth(year: 2026, month: month)

        #expect(PocketFormat.monthName(ref, capitalized: false) == plain)
        #expect(PocketFormat.monthName(ref, capitalized: true) == capitalized)
    }
}
