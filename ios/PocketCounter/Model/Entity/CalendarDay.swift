import Foundation

/// A day on the calendar with no time and no time zone: "3 October 2026".
struct CalendarDay: Hashable, Comparable, Sendable, Codable {
    enum Invalid: Error, Equatable {
        case format(String)
        case unrepresentable
        case month(Int)
        case day(Int, month: Int, year: Int)
    }

    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) throws {
        guard (1...12).contains(month) else { throw Invalid.month(month) }
        guard (1...Self.daysIn(month: month, year: year)).contains(day) else {
            throw Invalid.day(day, month: month, year: year)
        }
        self.year = year
        self.month = month
        self.day = day
    }

    static func first(of ref: RefYearMonth) -> CalendarDay {
        CalendarDay(unchecked: ref.year, ref.month, 1)
    }

    private init(unchecked year: Int, _ month: Int, _ day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    static func < (lhs: CalendarDay, rhs: CalendarDay) -> Bool {
        (lhs.year, lhs.month, lhs.day) < (rhs.year, rhs.month, rhs.day)
    }

    static func containing(_ date: Date, calendar: Calendar = .current) throws -> CalendarDay {
        try CalendarDay(
            year: calendar.component(.year, from: date),
            month: calendar.component(.month, from: date),
            day: calendar.component(.day, from: date)
        )
    }

    /// Always Gregorian: the backend speaks Gregorian ISO dates whatever calendar the user reads.
    static func today(in timeZone: TimeZone = .current, now: Date = .now) -> CalendarDay {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let parts = calendar.dateComponents([.year, .month, .day], from: now)
        return CalendarDay(unchecked: parts.year ?? 1970, parts.month ?? 1, parts.day ?? 1)
    }

    /// Noon, not midnight: midnight does not exist on some zones' daylight-saving days.
    func date(in timeZone: TimeZone) throws -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        let components = DateComponents(year: year, month: month, day: day, hour: 12)
        guard let date = calendar.date(from: components) else { throw Invalid.unrepresentable }
        return date
    }

    /// Shifts by whole days. Goes through `date(in:)`, which lands on noon precisely so a
    /// daylight-saving day cannot move the result.
    func adding(days: Int) -> CalendarDay? {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = .gmt
        guard let date = try? date(in: .gmt),
              let shifted = calendar.date(byAdding: .day, value: days, to: date)
        else { return nil }
        return try? CalendarDay.containing(shifted, calendar: calendar)
    }

    var refYearMonth: RefYearMonth { RefYearMonth(containing: self) }

    init(from decoder: Decoder) throws {
        try self.init(iso: decoder.singleValueContainer().decode(String.self))
    }

    init(iso text: String) throws {
        let parts = text.split(separator: "-", omittingEmptySubsequences: false)
        guard parts.map(\.count) == [4, 2, 2], parts.allSatisfy({ $0.allSatisfy(\.isASCIIDigit) }),
              let year = Int(parts[0]), let month = Int(parts[1]), let day = Int(parts[2])
        else { throw Invalid.format(text) }
        try self.init(year: year, month: month, day: day)
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(iso)
    }

    var iso: String { String(format: "%04d-%02d-%02d", year, month, day) }

    private static func daysIn(month: Int, year: Int) -> Int {
        switch month {
        case 2: isLeap(year) ? 29 : 28
        case 4, 6, 9, 11: 30
        default: 31
        }
    }

    private static func isLeap(_ year: Int) -> Bool {
        (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }
}

private extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}
