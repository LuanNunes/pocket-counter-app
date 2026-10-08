/// What the server understood of one sentence. Nothing is saved until the user confirms.
struct SentenceReading: Hashable, Sendable {
    let type: Sourced<TransactionType>?
    /// A magnitude: the write takes it positive and the endpoint carries the direction.
    let amount: Sourced<Money>?
    /// With no date in the sentence the server echoes the reference date.
    let date: Sourced<CalendarDay>
    let name: Sourced<String>?
    let paymentMethod: Sourced<PaymentMethod>?
    let card: CardReading
    let tag: TagID?
    /// The questions to ask, in order. Decoded, not derived: the server owns which.
    let missing: [MissingField]
}
