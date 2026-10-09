import Foundation
import Testing
@testable import PocketCounter

struct CalendarDayTodayTests {
    // 2026-10-09 15:00 UTC
    private let now = Date(timeIntervalSince1970: 1_791_558_000)

    @Test func answersTheGregorianDate() throws {
        #expect(CalendarDay.today(in: .gmt, now: now) == (try CalendarDay(year: 2026, month: 10, day: 9)))
    }

    // Unit-level pin only: a regression putting `Calendar.current` back inside `today` fails nothing
    // here (the host calendar is Gregorian). The structural guard is the non-optional factory.
    @Test(arguments: [Calendar.Identifier.buddhist, .hebrew, .islamic, .persian, .japanese, .coptic])
    func nonGregorianCalendarReadsADifferentDayThanToday(_ id: Calendar.Identifier) throws {
        var other = Calendar(identifier: id)
        other.timeZone = .gmt
        let read = try? CalendarDay.containing(now, calendar: other)
        #expect(read != CalendarDay.today(in: .gmt, now: now))
    }

    @Test func followsTheTimeZone() throws {
        let auckland = try #require(TimeZone(identifier: "Pacific/Auckland"))
        #expect(CalendarDay.today(in: auckland, now: now) == (try CalendarDay(year: 2026, month: 10, day: 10)))
    }
}
