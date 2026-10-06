import Foundation

/// Presentation formatting.
///
/// The locale is pinned to pt-BR rather than inherited from the device: the app is
/// Brazilian-only, and a user whose phone is in English would otherwise see "$" and
/// capitalised month names.
enum PocketFormat {

    static let locale = Locale(identifier: "pt_BR")

    /// Formats an amount as BRL. Pass `signed: false` where the column already says whether
    /// it is money in or money out, so the minus sign would be noise.
    static func currency(_ amount: Decimal, signed: Bool = true) -> String {
        let value = signed ? amount : abs(amount)
        return value.formatted(.currency(code: "BRL").locale(locale))
    }

    /// The lower-case in-sentence form: day labels and the month pill's VoiceOver value.
    /// `monthName` is the standalone form.
    ///
    /// `month` is 1-12; anything else is a programming error and traps.
    static func monthLabel(year: Int, month: Int, showingYear: Bool = false) -> String {
        let name = monthNames[month - 1].lowercased(with: locale)

        guard showingYear else { return name }

        return "\(name) de \(year)"
    }

    /// The standalone form, which the month pill capitalises (`store.jsx:7-8`).
    static func monthName(_ ref: RefYearMonth, capitalized: Bool) -> String {
        let name = monthNames[ref.month - 1].lowercased(with: locale)

        guard capitalized else { return name }

        return name.prefix(1).uppercased(with: locale) + name.dropFirst()
    }

    /// Standalone month symbols, which are the ones used on their own rather than inside a
    /// date. Foundation capitalises them in some locales, hence the lower-casing above.
    private static let monthNames: [String] = {
        let formatter = DateFormatter()
        formatter.locale = locale
        return formatter.standaloneMonthSymbols
    }()
}
