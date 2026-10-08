import Foundation

/// The working copy of a reading: each field with its badge, and the questions still open.
/// Every edit returns a new draft and marks the field `defined`.
struct ReadingDraft: Hashable, Sendable {
    private(set) var type: DraftField<TransactionType>?
    private(set) var amount: DraftField<Money>?
    private(set) var date: DraftField<CalendarDay>
    private(set) var name: DraftField<String>?
    private(set) var paymentMethod: DraftField<PaymentMethod>?
    private(set) var card: DraftField<CardCandidate>?
    private(set) var tag: TagID?
    private(set) var cardChoices: [CardCandidate]
    private(set) var cardSkipped = false
    let cardReading: CardReading
    let pending: [MissingField]

    init(_ reading: SentenceReading) {
        type = reading.type.map(DraftField.init)
        amount = reading.amount.map(DraftField.init)
        date = DraftField(reading.date)
        name = reading.name.map(DraftField.init)
        paymentMethod = reading.paymentMethod.map(DraftField.init)
        tag = reading.tag
        pending = reading.missing
        cardReading = reading.card
        switch reading.card {
        case .resolved(let resolved):
            card = DraftField(resolved)
            cardChoices = []
        case .ambiguous(let candidates):
            cardChoices = candidates
        case .unresolved, .notApplicable:
            cardChoices = []
        }
    }

    var nextQuestion: MissingField? { pending.first { !satisfies($0) } }

    func settingType(_ value: TransactionType) -> ReadingDraft { changing { $0.type = .defined(value) } }
    func settingAmount(_ value: Money) -> ReadingDraft { changing { $0.amount = .defined(value) } }
    func settingDate(_ value: CalendarDay) -> ReadingDraft { changing { $0.date = .defined(value) } }
    func settingCard(_ value: CardCandidate) -> ReadingDraft { changing { $0.card = .defined(value) } }
    func settingTag(_ value: TagID?) -> ReadingDraft { changing { $0.tag = value } }
    func skippingCard() -> ReadingDraft { changing { $0.cardSkipped = true } }

    func settingPaymentMethod(_ value: PaymentMethod?) -> ReadingDraft {
        changing { $0.paymentMethod = value.map(DraftField.defined) }
    }

    /// Blank clears it: a name of spaces does not name the row.
    func settingName(_ value: String) -> ReadingDraft {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return changing { $0.name = trimmed.isEmpty ? nil : .defined(trimmed) }
    }

    private func satisfies(_ field: MissingField) -> Bool {
        switch field {
        case .amount: amount != nil
        case .description: name != nil
        case .card: card != nil || cardSkipped
        case .type: type != nil
        }
    }

    private func changing(_ change: (inout ReadingDraft) -> Void) -> ReadingDraft {
        var copy = self
        change(&copy)
        return copy
    }
}
