import Testing

@testable import PocketCounter

@Suite("MonthWindow")
struct MonthWindowTests {

    private func ref(_ year: Int, _ month: Int) throws -> RefYearMonth {
        try RefYearMonth(year: year, month: month)
    }

    @Test("a window spans January of the previous year to December of the next", arguments: [
        (2026, 1), (2026, 6), (2026, 12),
    ])
    func bounds(year: Int, month: Int) throws {
        let window = MonthWindow.around(try ref(year, month))

        #expect(window.first == (try ref(year - 1, 1)))
        #expect(window.last == (try ref(year + 1, 12)))
    }

    @Test("january and december of a year are the calendar edges")
    func yearEdges() throws {
        #expect(RefYearMonth.january(of: 2026) == (try ref(2026, 1)))
        #expect(RefYearMonth.december(of: 2026) == (try ref(2026, 12)))
    }

    @Test("a window holds exactly 36 months")
    func length() throws {
        let window = MonthWindow.around(try ref(2026, 10))
        var count = 1
        var cursor = window.first

        while cursor < window.last {
            cursor = cursor.next()
            count += 1
        }

        #expect(count == 36)
    }

    @Test("both edges are inside and the months just beyond them are not")
    func containment() throws {
        let window = MonthWindow.around(try ref(2026, 10))

        #expect(window.contains(window.first))
        #expect(window.contains(window.last))
        #expect(!window.contains(window.first.previous()))
        #expect(!window.contains(window.last.next()))
        #expect(window.contains(try ref(2026, 10)))
    }

    @Test("clamping leaves a month inside alone and pulls one outside to the nearest edge")
    func clamping() throws {
        let window = MonthWindow.around(try ref(2026, 10))

        #expect(window.clamped(window.first.previous()) == window.first)
        #expect(window.clamped(try ref(2026, 3)) == (try ref(2026, 3)))
        #expect(window.clamped(window.last.next()) == window.last)
        #expect(window.clamped(try ref(1999, 1)) == window.first)
    }

    @Test("the window depends only on the anchor, never on the clock")
    func anchorOnly() throws {
        #expect(MonthWindow.around(try ref(2020, 5)).first == (try ref(2019, 1)))
    }
}
