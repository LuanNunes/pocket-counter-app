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
    private(set) var tag: DraftField<TagID>?
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
        // The server sends no `source.tag`: a suggested tag is its inference, so `.fromSentence` would lie.
        tag = reading.tag.map { DraftField(value: $0, provenance: .assumed) }
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
    func settingTag(_ value: TagID?) -> ReadingDraft { changing { $0.tag = value.map(DraftField.defined) } }
    func skippingCard() -> ReadingDraft { changing { $0.cardSkipped = true } }

    /// Naming a card is naming credit: the server treats a row as a card charge only when the
    /// method is CREDIT, so a card under any other method is neither a charge nor a clean row.
    func settingCard(_ value: CardCandidate) -> ReadingDraft {
        changing {
            $0.card = .defined(value)
            $0.paymentMethod = .defined(.credit)
        }
    }

    /// Leaving credit drops the card.
    func settingPaymentMethod(_ value: PaymentMethod?) -> ReadingDraft {
        changing {
            $0.paymentMethod = value.map(DraftField.defined)
            guard value != .credit else { return }
            $0.card = nil
        }
    }

    /// `nil` when a required field is absent or `TransactionEntry` refuses what is there. Does not
    /// consult `nextQuestion`. The card travels whenever present, whatever its provenance.
    func confirmed() -> TransactionEntry? {
        guard let type, let amount, let name else { return nil }
        return TransactionEntry(
            type: type.value, amount: amount.value, date: date.value, name: name.value,
            paymentMethod: paymentMethod?.value, card: card?.value.id, tag: tag?.value
        )
    }

    /// Blank clears it: a name of spaces does not name the row.
    func settingName(_ value: String) -> ReadingDraft {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return changing { $0.name = trimmed.isEmpty ? nil : .defined(trimmed) }
    }

    private var isNotCardCharge: Bool {
        guard let method = paymentMethod?.value else { return false }
        return method != .credit
    }

    private func satisfies(_ field: MissingField) -> Bool {
        switch field {
        case .amount: amount != nil
        case .description: name != nil
        case .card: card != nil || cardSkipped || isNotCardCharge
        case .type: type != nil
        }
    }

    private func changing(_ change: (inout ReadingDraft) -> Void) -> ReadingDraft {
        var copy = self
        change(&copy)
        return copy
    }
}
