import Foundation

/// Sample amounts for previews and the design-system smoke screen.
///
/// Integer cents: a `Decimal` literal written as `-184.90` goes through `Double`, and
/// `Decimal(string:)` is failable, so neither belongs in a view.
enum PreviewMoney {
    static let groceries = cents(-18_490)
    static let salary = cents(720_000)
    static let cardBill = cents(-124_055)

    static let balance = cents(577_455)
    static let expenses = cents(342_545)
    static let incomes = cents(920_000)
    static let pending = cents(124_055)

    private static func cents(_ value: Int) -> Decimal {
        Decimal(value) / 100
    }
}
