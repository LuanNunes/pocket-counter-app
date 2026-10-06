import Testing

@testable import PocketCounter

@Suite("RefYearMonthRange")
struct RefYearMonthRangeTests {

    private func ref(_ year: Int, _ month: Int) throws -> RefYearMonth {
        try RefYearMonth(year: year, month: month)
    }

    @Test("a single month is a range of one")
    func single() throws {
        let october = try ref(2026, 10)

        let range = try RefYearMonthRange(from: october, through: october)

        #expect(range.months == [october])
    }

    @Test("months run ascending and inclusive across the year boundary")
    func rollover() throws {
        let range = try RefYearMonthRange(from: ref(2026, 11), through: ref(2027, 2))

        #expect(range.months.map(\.raw) == [202611, 202612, 202701, 202702])
    }

    @Test("twelve months are accepted")
    func twelve() throws {
        let range = try RefYearMonthRange(from: ref(2026, 1), through: ref(2026, 12))

        #expect(range.months.count == RefYearMonthRange.maximumMonths)
    }

    @Test("thirteen months are rejected with the count")
    func thirteen() throws {
        let from = try ref(2026, 1)
        let through = try ref(2027, 1)

        #expect(throws: RefYearMonthRange.Invalid.tooLong(months: 13)) {
            try RefYearMonthRange(from: from, through: through)
        }
    }

    @Test("a reversed range is rejected")
    func reversed() throws {
        let from = try ref(2026, 10)
        let through = try ref(2026, 9)

        #expect(throws: RefYearMonthRange.Invalid.reversed) {
            try RefYearMonthRange(from: from, through: through)
        }
    }
}
