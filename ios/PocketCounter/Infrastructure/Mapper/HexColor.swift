import Foundation

enum HexColor {
    /// Never throws: a colour is decoration, so absence and garbage both answer `nil`.
    static func argb(_ text: String?) -> UInt32? {
        guard let text else { return nil }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        let digits = trimmed.hasPrefix("#") ? String(trimmed.dropFirst()) : trimmed
        guard digits.count == 6 || digits.count == 8, digits.allSatisfy(\.isHexDigit) else { return nil }
        guard let value = UInt32(digits, radix: 16) else { return nil }
        return digits.count == 6 ? 0xFF00_0000 | value : value
    }
}
