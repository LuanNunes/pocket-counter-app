import Foundation
import Testing
@testable import PocketCounter

struct CalendarDayTodayTests {
    // 2026-10-09 15:00 UTC
    private let now = Date(timeIntervalSince1970: 1_791_558_000)

    @Test func answersTheGregorianDate() throws {
        #expect(CalendarDay.today(in: .gmt, now: now) == (try CalendarDay(year: 2026, month: 10, day: 9)))
    }

    @Test(arguments: [Calendar.Identifier.buddhist, .hebrew, .islamic, .persian, .japanese, .coptic])
    func ignoresTheUsersCalendar(_ id: Calendar.Identifier) throws {
        var other = Calendar(identifier: id)
        other.timeZone = .gmt
        let read = try? CalendarDay.containing(now, calendar: other)
        #expect(read != CalendarDay.today(in: .gmt, now: now))
    }

    @Test func followsTheTimeZone() throws {
        let tokyo = try #require(TimeZone(identifier: "Pacific/Auckland"))
        #expect(CalendarDay.today(in: tokyo, now: now) == (try CalendarDay(year: 2026, month: 10, day: 10)))
    }
}
