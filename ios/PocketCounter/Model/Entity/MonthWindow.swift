import Foundation

/// The months a screen may select: January of the previous year through December of the next
/// (36). Not `RefYearMonthRange`, which caps at the backend range endpoint's 12.
///
/// Anchored once, so it does not move if the app stays open across a new year.
struct MonthWindow: Hashable, Sendable {
    let first: RefYearMonth
    let last: RefYearMonth

    static func around(_ ref: RefYearMonth) -> MonthWindow {
        MonthWindow(first: .january(of: ref.year - 1), last: .december(of: ref.year + 1))
    }

    func contains(_ ref: RefYearMonth) -> Bool {
        (first...last).contains(ref)
    }

    func clamped(_ ref: RefYearMonth) -> RefYearMonth {
        min(max(ref, first), last)
    }
}
