import Foundation

struct RecurringSeries: Equatable, Sendable {
    let id: SeriesID
    let name: String
    let type: TransactionType
    let recurrenceDay: Int?
}
