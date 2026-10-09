import Foundation

/// Presentation formatting.
///
/// The locale is pinned to pt-BR rather than inherited from the device: the app is
/// Brazilian-only, and a user whose phone is in English would otherwise see "$" and
/// capitalised month names.
enum PocketFormat {

    static let locale = Locale(identifier: "pt_BR")

    /// U+2212, which is what the design draws; Foundation emits a hyphen-minus.
    static let minusSign = "\u{2212}"

    /// Formats an amount as BRL. Pass `signed: false` where the column already says whether
    /// it is money in or money out, so the minus sign would be noise.
    static func currency(_ amount: Decimal, signed: Bool = true) -> String {
        let value = signed ? amount : abs(amount)
        let text = value.formatted(.currency(code: "BRL").locale(locale))
        return text.replacingOccurrences(of: "-", with: minusSign)
    }

    /// For VoiceOver, which cannot be trusted to read U+2212.
    static func spokenCurrency(_ amount: Decimal) -> String {
        guard amount < 0 else { return currency(amount) }
        return "menos " + currency(amount, signed: false)
    }

    /// `+ R$ 10,00` or `− R$ 10,00`, the spec's row amount.
    /// `tx.jsx:38` draws the sign from the kind, not from the amount, so a zero keeps its direction.
    static func movement(_ amount: Decimal, isIncome: Bool) -> String {
        (isIncome ? "+" : minusSign) + " " + currency(amount, signed: false)
    }

    static func spokenMovement(_ amount: Decimal, isIncome: Bool) -> String {
        (isIncome ? "mais " : "menos ") + currency(amount, signed: false)
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

    /// "19 de maio".
    static func dayLabel(_ day: CalendarDay) -> String {
        "\(day.day) de \(monthLabel(year: day.year, month: day.month))"
    }

    /// `07/10`, the review's compact day (`quickadd.jsx`'s `ddmm`).
    static func shortDay(_ day: CalendarDay) -> String {
        String(format: "%02d/%02d", day.day, day.month)
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
