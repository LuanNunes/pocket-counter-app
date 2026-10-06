import Foundation

struct HistoryItem: Hashable, Sendable {
    let id: TransactionID
    /// The month the row belongs to, which a due date may fall outside of.
    let ref: RefYearMonth
    let date: CalendarDay
    /// Expenses carry a negative sign.
    let amount: Money
    let type: TransactionType
    /// The transaction's own tags. `nil` means no own tags; non-`nil` (even empty) is an override.
    let tagIds: [TagID]?
    let statusPayment: PaymentStatus
    let displayOrder: Int
    let paymentMethod: PaymentMethod?
    let cardId: CardID?
    let seriesId: String?
    let name: String?
    let description: String?
    let isInvoice: Bool

    var isFixo: Bool { seriesId != nil }

    init(
        id: TransactionID,
        ref: RefYearMonth,
        date: CalendarDay,
        amount: Money,
        type: TransactionType,
        tagIds: [TagID]?,
        statusPayment: PaymentStatus = .paid,
        displayOrder: Int = 0,
        paymentMethod: PaymentMethod? = nil,
        cardId: CardID? = nil,
        seriesId: String? = nil,
        name: String? = nil,
        description: String? = nil,
        isInvoice: Bool = false
    ) {
        self.id = id
        self.ref = ref
        self.date = date
        self.amount = amount
        self.type = type
        self.tagIds = tagIds
        self.statusPayment = statusPayment
        self.displayOrder = displayOrder
        self.paymentMethod = paymentMethod
        self.cardId = cardId
        self.seriesId = seriesId
        self.name = name
        self.description = description
        self.isInvoice = isInvoice
    }

    func displayTitle() -> String {
        [name, description]
            .compactMap { $0 }
            .first { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            ?? "—"
    }
}
