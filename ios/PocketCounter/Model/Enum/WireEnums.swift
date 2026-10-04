import Foundation

// Raw values are the backend's UPPERCASE strings; unknown values yield nil from `init?(wire:)`
// so the mapper decides how to degrade instead of the decoder crashing.

enum TransactionType: String, Hashable, Sendable {
    case income = "INCOME"
    case expense = "EXPENSE"
}

enum PaymentStatus: String, Hashable, Sendable {
    case paid = "PAID"
    case pending = "PENDING"
}

enum PaymentMethod: String, Hashable, Sendable {
    case credit = "CREDIT"
    case debit = "DEBIT"
    case pix = "PIX"
    case cash = "CASH"
    case crypto = "CRYPTO"
}

extension TransactionType {
    init?(wire: String) { self.init(rawValue: wire) }
    var wire: String { rawValue }
}

extension PaymentStatus {
    init?(wire: String) { self.init(rawValue: wire) }
    var wire: String { rawValue }
}

extension PaymentMethod {
    init?(wire: String) { self.init(rawValue: wire) }
    var wire: String { rawValue }
}
