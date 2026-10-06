import Foundation
import Testing

@testable import PocketCounter

@Suite("CalendarDay")
struct CalendarDayTests {

    @Test("a valid day keeps its components")
    func components() throws {
        let day = try CalendarDay(year: 2026, month: 10, day: 3)

        #expect(day.year == 2026)
        #expect(day.month == 10)
        #expect(day.day == 3)
    }

    @Test("a month outside 1-12 is rejected", arguments: [0, 13])
    func rejectsInvalidMonth(month: Int) {
        #expect(throws: CalendarDay.Invalid.month(month)) {
            try CalendarDay(year: 2026, month: month, day: 1)
        }
    }

    @Test("a day the month does not have is rejected", arguments: [0, 32, -1])
    func rejectsInvalidDay(day: Int) {
        #expect(throws: CalendarDay.Invalid.day(day, month: 10, year: 2026)) {
            try CalendarDay(year: 2026, month: 10, day: day)
        }
    }

    @Test("February 29 exists only in leap years", arguments: [(2024, true), (2025, false), (1900, false), (2000, true)])
    func leapDay(year: Int, valid: Bool) {
        let accepted = (try? CalendarDay(year: year, month: 2, day: 29)) != nil

        #expect(accepted == valid)
    }

    @Test("a month's last day is accepted and the next is not", arguments: [(1, 31), (4, 30), (2, 28)])
    func monthLength(month: Int, last: Int) throws {
        _ = try CalendarDay(year: 2025, month: month, day: last)

        #expect(throws: CalendarDay.Invalid.day(last + 1, month: month, year: 2025)) {
            try CalendarDay(year: 2025, month: month, day: last + 1)
        }
    }

    @Test("days order chronologically across days, months and years")
    func ordering() throws {
        let earlier = try CalendarDay(year: 2025, month: 12, day: 31)
        let sameMonth = try CalendarDay(year: 2026, month: 1, day: 2)
        let laterMonth = try CalendarDay(year: 2026, month: 2, day: 1)
        let laterYear = try CalendarDay(year: 2027, month: 1, day: 1)

        #expect(earlier < sameMonth)
        #expect(sameMonth < laterMonth)
        #expect(laterMonth < laterYear)
        #expect([laterYear, earlier, laterMonth, sameMonth].sorted() == [earlier, sameMonth, laterMonth, laterYear])
    }

    @Test("it encodes as an ISO yyyy-MM-dd string and decodes back to the same day")
    func isoRoundTrip() throws {
        let day = try CalendarDay(year: 2026, month: 1, day: 5)

        let data = try JSONEncoder().encode([day])
        let decoded = try JSONDecoder().decode([CalendarDay].self, from: data)

        #expect(String(decoding: data, as: UTF8.self) == #"["2026-01-05"]"#)
        #expect(decoded == [day])
    }

    @Test("decoding a string that is not a real yyyy-MM-dd day fails with a typed error",
          arguments: ["ontem", "2026-10", "2026-10-3", "2026-10-03T10:00:00Z", "26-10-03", "2026-1a-03", "+026-10-03", ""])
    func rejectsMalformedText(text: String) {
        #expect(throws: CalendarDay.Invalid.format(text)) {
            try JSONDecoder().decode(CalendarDay.self, from: Data("\"\(text)\"".utf8))
        }
    }

    @Test("decoding a well-formed string with an impossible date reports the field that is wrong")
    func rejectsImpossibleDate() {
        #expect(throws: CalendarDay.Invalid.month(13)) {
            try JSONDecoder().decode(CalendarDay.self, from: Data(#""2026-13-01""#.utf8))
        }
        #expect(throws: CalendarDay.Invalid.day(29, month: 2, year: 2025)) {
            try JSONDecoder().decode(CalendarDay.self, from: Data(#""2025-02-29""#.utf8))
        }
    }

    @Test("iso parses a yyyy-MM-dd string directly")
    func isoParses() throws {
        #expect(try CalendarDay(iso: "2026-10-03") == CalendarDay(year: 2026, month: 10, day: 3))
    }

    @Test("iso rejects malformed text with the format error")
    func isoRejectsFormat() {
        #expect(throws: CalendarDay.Invalid.format("2026-10-3")) { try CalendarDay(iso: "2026-10-3") }
    }

    @Test("iso reports a well-formed impossible date by the field that is wrong")
    func isoRejectsImpossibleDate() {
        #expect(throws: CalendarDay.Invalid.month(13)) { try CalendarDay(iso: "2026-13-01") }
        #expect(throws: CalendarDay.Invalid.day(29, month: 2, year: 2025)) { try CalendarDay(iso: "2025-02-29") }
    }

    @Test("it converts to the month it falls in, including the December rollover")
    func refYearMonth() throws {
        let october = try CalendarDay(year: 2026, month: 10, day: 31)
        let december = try CalendarDay(year: 2026, month: 12, day: 31)

        #expect(october.refYearMonth.raw == 202610)
        #expect(december.refYearMonth.next().raw == 202701)
    }

    // 02:30 UTC on 4 Oct is still 3 Oct at 23:30 in São Paulo (UTC-3).
    @Test("the day containing an instant depends on the time zone it is read in")
    func containingInstant() throws {
        let instant = try #require(ISO8601DateFormatter().date(from: "2026-10-04T02:30:00Z"))

        #expect(try CalendarDay.containing(instant, calendar: .gregorian(in: "UTC")) == CalendarDay(year: 2026, month: 10, day: 4))
        #expect(try CalendarDay.containing(instant, calendar: .gregorian(in: "America/Sao_Paulo")) == CalendarDay(year: 2026, month: 10, day: 3))
    }

    @Test("a day converted to a Date reads back as the same day in any zone", arguments: ["UTC", "America/Sao_Paulo", "Asia/Tokyo", "Pacific/Kiritimati", "Pacific/Pago_Pago"])
    func sameDayAcrossZones(zone: String) throws {
        let day = try CalendarDay(year: 2026, month: 3, day: 1)
        let date = try day.date(in: #require(TimeZone(identifier: zone)))

        #expect(try CalendarDay.containing(date, calendar: .gregorian(in: zone)) == day)
    }

    @Test("the Date is noon in the given zone, even on a day whose midnight did not exist")
    func noon() throws {
        let saoPaulo = try #require(TimeZone(identifier: "America/Sao_Paulo"))
        let dstStart = try CalendarDay(year: 2018, month: 11, day: 4)
        let calendar = Calendar.gregorian(in: "America/Sao_Paulo")

        let date = try dstStart.date(in: saoPaulo)

        #expect(calendar.component(.hour, from: date) == 12)
        #expect(calendar.component(.minute, from: date) == 0)
    }
}

extension Calendar {
    static func gregorian(in zone: String) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        guard let timeZone = TimeZone(identifier: zone) else { fatalError("unknown time zone \(zone)") }
        calendar.timeZone = timeZone
        return calendar
    }
}
