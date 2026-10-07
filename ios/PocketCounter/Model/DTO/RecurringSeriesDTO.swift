import Foundation

/// The backend declares `id` nullable, so every field is optional and the mapper decides.
struct RecurringSeriesDTO: Decodable, Sendable {
    let id: String?
    let name: String?
    let transactionType: String?
    let recurrenceDay: Int?
}
