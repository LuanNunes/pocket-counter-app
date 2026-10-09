import Foundation

/// Reads the free numeric answer to `MissingField.amount`. Text in, exact `Decimal` out: no `Double`.
enum AmountEntry {
    /// pt-BR notation: `.` groups thousands, one `,` opens the fraction. A lone `.` not followed by
    /// exactly three digits is the decimal point. Stricter than the sentence parser, which rounds.
    static func money(_ typed: String) -> Money? {
        let stripped = typed.filter { !$0.isWhitespace }
        let text = decimalDot(stripped.lowercased().hasPrefix("r$") ? String(stripped.dropFirst(2)) : stripped)
        let sides = text.split(separator: ",", omittingEmptySubsequences: false)
        guard let whole = sides.first, sides.count <= 2,
              let digits = integerDigits(String(whole))
        else { return nil }
        let fraction = sides.count == 2 ? String(sides[1]) : nil
        if let fraction, !(1...2).contains(fraction.count) || !isDigits(fraction) { return nil }
        guard let amount = Decimal(string: fraction.map { "\(digits).\($0)" } ?? digits, locale: nil),
              amount > 0
        else { return nil }
        return Money(amount)
    }

    private static func decimalDot(_ text: String) -> String {
        let parts = text.split(separator: ".", omittingEmptySubsequences: false)
        guard !text.contains(","), parts.count == 2, parts[1].count != 3 else { return text }
        return parts.joined(separator: ",")
    }

    private static func integerDigits(_ whole: String) -> String? {
        let groups = whole.split(separator: ".", omittingEmptySubsequences: false).map(String.init)
        guard let head = groups.first, isDigits(head), groups.dropFirst().allSatisfy({ $0.count == 3 && isDigits($0) })
        else { return nil }
        guard groups.count > 1 else { return head }
        return head.count <= 3 ? groups.joined() : nil
    }

    private static func isDigits(_ text: String) -> Bool {
        !text.isEmpty && text.allSatisfy { ("0"..."9").contains($0) }
    }
}
