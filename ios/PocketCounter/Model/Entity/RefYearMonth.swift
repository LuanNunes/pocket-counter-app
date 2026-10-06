import Foundation

/// A calendar month encoded as `yyyyMM` (October 2026 is `202610`), the form the backend uses.
/// The month is always 1-12 by construction.
struct RefYearMonth: Hashable, Comparable, Sendable {
    enum Invalid: Error, Equatable {
        case month(Int)
    }

    let raw: Int

    var year: Int { raw / 100 }
    var month: Int { raw % 100 }

    static var current: RefYearMonth {
        containing(.now)
    }

    init(year: Int, month: Int) throws {
        guard (1...12).contains(month) else { throw Invalid.month(month) }
        raw = year * 100 + month
    }

    init?(raw: Int) {
        guard let ref = try? RefYearMonth(year: raw / 100, month: raw % 100) else { return nil }
        self = ref
    }

    init(containing day: CalendarDay) {
        self.init(unchecked: day.year * 100 + day.month)
    }

    static func containing(_ date: Date, calendar: Calendar = .current) -> RefYearMonth {
        RefYearMonth(unchecked: calendar.component(.year, from: date) * 100 + calendar.component(.month, from: date))
    }

    static func january(of year: Int) -> RefYearMonth {
        RefYearMonth(unchecked: year * 100 + 1)
    }

    static func december(of year: Int) -> RefYearMonth {
        RefYearMonth(unchecked: year * 100 + 12)
    }

    func next() -> RefYearMonth {
        RefYearMonth(unchecked: month == 12 ? raw + 89 : raw + 1)
    }

    func previous() -> RefYearMonth {
        RefYearMonth(unchecked: month == 1 ? raw - 89 : raw - 1)
    }

    static func < (lhs: RefYearMonth, rhs: RefYearMonth) -> Bool {
        lhs.raw < rhs.raw
    }

    private init(unchecked raw: Int) {
        self.raw = raw
    }
}
