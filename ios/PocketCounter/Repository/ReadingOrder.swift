import Foundation

enum ReadingOrder {
    static let locale = Locale(identifier: "pt_BR")

    /// The server has no `ORDER BY` here. Ties fall to `id`, because the sort is unstable.
    static func byName<T>(
        _ items: [T],
        locale: Locale = ReadingOrder.locale,
        name: (T) -> String,
        id: (T) -> String
    ) -> [T] {
        items.sorted { lhs, rhs in
            switch compare(name(lhs), name(rhs), locale: locale) {
            case .orderedAscending: return true
            case .orderedDescending: return false
            case .orderedSame: return id(lhs) < id(rhs)
            }
        }
    }

    /// The server sorts on a nullable column with no tie-break: nulls go last, then name, then `id`.
    static func categories(_ categories: [CategoryDTO]) -> [CategoryDTO] {
        categories.sorted { lhs, rhs in
            switch (lhs.displayOrder, rhs.displayOrder) {
            case (let left?, let right?) where left != right: return left < right
            case (nil, .some): return false
            case (.some, nil): return true
            case (.some, .some), (nil, nil): break
            }
            switch compare(lhs.name, rhs.name, locale: locale) {
            case .orderedAscending: return true
            case .orderedDescending: return false
            case .orderedSame: return lhs.id < rhs.id
            }
        }
    }

    private static func compare(_ lhs: String, _ rhs: String, locale: Locale) -> ComparisonResult {
        let options: String.CompareOptions = [.caseInsensitive, .diacriticInsensitive, .numeric, .forcedOrdering]
        return lhs.compare(rhs, options: options, range: nil, locale: locale)
    }
}
