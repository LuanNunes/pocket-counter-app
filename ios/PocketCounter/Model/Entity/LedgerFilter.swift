import Foundation

struct LedgerFilter: Hashable, Sendable {
    let kind: TransactionType
    let query: String
    let onlyFixos: Bool

    func apply(to items: [HistoryItem], lookups: LookupSet) -> [HistoryItem] {
        items.filter { item in
            item.type == kind && (!onlyFixos || item.isFixo) && matches(item, lookups: lookups)
        }
    }

    private func matches(_ item: HistoryItem, lookups: LookupSet) -> Bool {
        let needle = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !needle.isEmpty else { return true }
        let texts = [item.displayTitle()]
            + item.effectiveTagIds(inheriting: []).compactMap { lookups.tagsById[$0]?.name }
        guard !texts.contains(where: { $0.range(of: needle, options: Self.textOptions) != nil }) else { return true }
        let digits = needle.filter(\.isASCIIDigit)
        return !digits.isEmpty && Self.cents(of: item.amount).contains(digits)
    }

    private static let textOptions: String.CompareOptions = [.caseInsensitive, .diacriticInsensitive]

    /// Two fixed decimals, so "10" finds a row shown as "R$ 10,50".
    private static func cents(of money: Money) -> String {
        var scaled = money.abs.amount * 100
        var rounded = Decimal()
        NSDecimalRound(&rounded, &scaled, 0, .plain)
        return "\(rounded)"
    }
}

private extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}
