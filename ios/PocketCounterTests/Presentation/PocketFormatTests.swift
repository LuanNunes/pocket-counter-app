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

    @Test("a negative amount keeps its sign")
    func currencyKeepsNegativeSign() {
        #expect(PocketFormat.currency(Decimal(string: "-12.30")!).contains("-"))
    }

    @Test("an amount can be rendered without its sign, for a column that labels it instead")
    func currencyCanDropTheSign() {
        let formatted = PocketFormat.currency(Decimal(string: "-12.30")!, signed: false)

        #expect(formatted.contains("-") == false)
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
}
