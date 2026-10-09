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
    let recurringTransactionId: RecurringTransactionID?
    let name: String?
    let description: String?
    let isInvoice: Bool

    var isFixo: Bool { recurringTransactionId != nil }

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
        recurringTransactionId: RecurringTransactionID? = nil,
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
        self.recurringTransactionId = recurringTransactionId
        self.name = name
        self.description = description
        self.isInvoice = isInvoice
    }

    func effectiveTagIds(inheriting inherited: [TagID]) -> [TagID] {
        tagIds ?? inherited
    }

    func settingPaymentStatus(_ status: PaymentStatus) -> HistoryItem {
        HistoryItem(
            id: id, ref: ref, date: date, amount: amount, type: type, tagIds: tagIds, statusPayment: status,
            displayOrder: displayOrder, paymentMethod: paymentMethod, cardId: cardId, recurringTransactionId: recurringTransactionId,
            name: name, description: description, isInvoice: isInvoice
        )
    }

    func settingDisplayOrder(_ order: Int) -> HistoryItem {
        HistoryItem(
            id: id, ref: ref, date: date, amount: amount, type: type, tagIds: tagIds, statusPayment: statusPayment,
            displayOrder: order, paymentMethod: paymentMethod, cardId: cardId, recurringTransactionId: recurringTransactionId,
            name: name, description: description, isInvoice: isInvoice
        )
    }

    var hasTitle: Bool { title != nil }

    func displayTitle() -> String { title ?? "—" }

    private var title: String? {
        [name, description]
            .compactMap { $0 }
            .first { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }
}
