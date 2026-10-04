import Foundation
import Testing

@testable import PocketCounter

@Suite("RefYearMonth")
struct RefYearMonthTests {

    @Test("October 2026 is 202610")
    func raw() throws {
        let ref = try RefYearMonth(year: 2026, month: 10)

        #expect(ref.raw == 202610)
        #expect(ref.year == 2026)
        #expect(ref.month == 10)
    }

    @Test("a month outside 1-12 is rejected", arguments: [0, 13, -1])
    func rejectsInvalidMonth(month: Int) {
        #expect(throws: RefYearMonth.Invalid.month(month)) {
            try RefYearMonth(year: 2026, month: month)
        }
    }

    @Test("a raw value parses only when its month is valid")
    func rawInit() {
        #expect(RefYearMonth(raw: 202601)?.month == 1)
        #expect(RefYearMonth(raw: 202612)?.month == 12)
        #expect(RefYearMonth(raw: 202600) == nil)
        #expect(RefYearMonth(raw: 202613) == nil)
        #expect(RefYearMonth(raw: 0) == nil)
    }

    @Test("next rolls the year over from December")
    func next() throws {
        #expect(try RefYearMonth(year: 2026, month: 12).next().raw == 202701)
        #expect(try RefYearMonth(year: 2026, month: 3).next().raw == 202604)
    }

    @Test("previous rolls the year back from January")
    func previous() throws {
        #expect(try RefYearMonth(year: 2026, month: 1).previous().raw == 202512)
        #expect(try RefYearMonth(year: 2026, month: 3).previous().raw == 202602)
    }

    @Test("months order chronologically across years")
    func ordering() throws {
        let dec = try RefYearMonth(year: 2025, month: 12)
        let jan = try RefYearMonth(year: 2026, month: 1)

        #expect(dec < jan)
        #expect([jan, dec].sorted() == [dec, jan])
    }

    @Test("the month containing a date follows the supplied calendar")
    func containing() throws {
        var calendar = Calendar(identifier: .gregorian)
        let utc = try #require(TimeZone(secondsFromGMT: 0))
        calendar.timeZone = utc
        let date = try #require(calendar.date(from: DateComponents(year: 2026, month: 10, day: 3)))

        #expect(RefYearMonth.containing(date, calendar: calendar).raw == 202610)
    }

    @Test("current is the month containing now")
    func current() {
        #expect(RefYearMonth.current == RefYearMonth.containing(.now))
    }
}
