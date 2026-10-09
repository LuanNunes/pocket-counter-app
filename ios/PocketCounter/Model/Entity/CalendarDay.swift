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
        return CalendarDay(
            unchecked: calendar.component(.year, from: now),
            calendar.component(.month, from: now),
            calendar.component(.day, from: now)
        )
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
    /// Shifts by whole days in proleptic Gregorian arithmetic, so no calendar or time zone is involved.
    func adding(days: Int) -> CalendarDay {
        let shifted = epochDay + days
        let z = shifted + 719_468
        let era = (z >= 0 ? z : z - 146_096) / 146_097
        let dayOfEra = z - era * 146_097
        let yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
        let dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
        let shiftedMonth = (5 * dayOfYear + 2) / 153
        let shiftedDay = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
        let calendarMonth = shiftedMonth < 10 ? shiftedMonth + 3 : shiftedMonth - 9
        let calendarYear = yearOfEra + era * 400 + (calendarMonth <= 2 ? 1 : 0)
        return CalendarDay(unchecked: calendarYear, calendarMonth, shiftedDay)
    }

    private var epochDay: Int {
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yearOfEra = y - era * 400
        let dayOfYear = (153 * (month > 2 ? month - 3 : month + 9) + 2) / 5 + day - 1
        let dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era * 146_097 + dayOfEra - 719_468
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
