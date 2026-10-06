import Foundation

/// Inclusive, ascending. The cap mirrors the backend, which answers 400 above 12 months.
struct RefYearMonthRange: Hashable, Sendable {
    enum Invalid: Error, Equatable {
        case reversed
        case tooLong(months: Int)
    }

    static let maximumMonths = 12

    let from: RefYearMonth
    let through: RefYearMonth
    let months: [RefYearMonth]

    init(from: RefYearMonth, through: RefYearMonth) throws(Invalid) {
        guard from <= through else { throw .reversed }
        let count = (through.year - from.year) * 12 + through.month - from.month + 1
        guard count <= Self.maximumMonths else { throw .tooLong(months: count) }
        self.from = from
        self.through = through
        self.months = (1..<count).reduce(into: [from]) { months, _ in months.append(months[months.count - 1].next()) }
    }
}
