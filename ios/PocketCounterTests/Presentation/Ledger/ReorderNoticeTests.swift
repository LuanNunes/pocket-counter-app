import Testing

@testable import PocketCounter

@Suite("ReorderNotice")
struct ReorderNoticeTests {
    private let october = RefYearMonth(raw: 202610)!
    private let november = RefYearMonth(raw: 202611)!

    private func failed(_ failure: WriteFailure = .server) -> FailedReorder {
        FailedReorder(ref: october, kind: .expense, failure: failure)
    }

    @Test("a failure in this month and kind shows the reorder message")
    func match() {
        let notice = ReorderNotice.message(for: failed(.unreachable), month: october, kind: .expense)

        #expect(notice == WriteFailureMessage.message(for: .unreachable, subject: .reordering))
        #expect(notice != nil)
    }

    @Test("no failure shows nothing")
    func none() {
        #expect(ReorderNotice.message(for: nil, month: october, kind: .expense) == nil)
    }

    @Test("a failure in another month shows nothing")
    func otherMonth() {
        #expect(ReorderNotice.message(for: failed(), month: november, kind: .expense) == nil)
    }

    @Test("a failure of the other kind shows nothing")
    func otherKind() {
        #expect(ReorderNotice.message(for: failed(), month: october, kind: .income) == nil)
    }

    @Test("an expired session shows nothing")
    func expired() {
        #expect(ReorderNotice.message(for: failed(.sessionExpired), month: october, kind: .expense) == nil)
    }
}
