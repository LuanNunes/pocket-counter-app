import Observation

typealias ReadSentenceAction = @Sendable (SentenceText, CalendarDay) async throws(ReadingFailure) -> SentenceOpening

typealias CreateTransactionAction = @Sendable (TransactionEntry) async throws(WriteFailure) -> Void

enum QuickAddStage: Equatable {
    case writing(String)
    case reading
    case asking(ReadingDraft)
    case reviewing(ReadingDraft)
    case saving(ReadingDraft)
    case saved(TransactionEntry)
}

/// How a question is answered, which decides both the control and the footer.
enum QuickAddQuestionKind: Equatable {
    /// Free text or digits: the footer is "Confirmar", enabled only when the answer parses.
    case typed(MissingField)
    /// A row per option: the footer is "Editar a frase", because there is nothing to confirm.
    case picked(MissingField)

    init(_ field: MissingField) {
        switch field {
        case .amount, .description: self = .typed(field)
        case .type, .card: self = .picked(field)
        }
    }
}

@MainActor
@Observable
final class QuickAddModel {
    struct State: Equatable {
        var stage: QuickAddStage = .writing("")
        var lookups = LookupSet(categories: [], tags: [], cards: [])
        var readingFailure: ReadingFailure?
        var writeFailure: WriteFailure?
        /// The server's sentence for a 409. Separate from `writeFailure`: this one has an answer.
        var duplicate: String?
        /// Kept past `send()`, so "Editar a frase" has something to return to.
        var sentence = ""
        /// Set when a write succeeds and never cleared while this model lives: the sheet can be
        /// swiped away on the receipt, and Início must still reload.
        fileprivate(set) var didWrite = false

        /// The review's "Lançar" is only live when the draft can actually become an entry: `pending`
        /// is the server's word, not a guarantee, and `TransactionEntry` can still refuse what is there.
        var canConfirm: Bool { draft?.confirmed() != nil }

        var draft: ReadingDraft? {
            switch stage {
            case .asking(let draft), .reviewing(let draft), .saving(let draft): draft
            case .writing, .reading, .saved: nil
            }
        }
    }

    private(set) var state = State()
    private let read: ReadSentenceAction
    private let create: CreateTransactionAction
    let today: CalendarDay

    init(
        read: @escaping ReadSentenceAction,
        create: @escaping CreateTransactionAction,
        today: CalendarDay
    ) {
        self.read = read
        self.create = create
        self.today = today
    }

    func type(_ text: String) {
        guard case .writing = state.stage else { return }
        state.sentence = text
        state.stage = .writing(text)
        state.readingFailure = nil
    }

    func send() async {
        guard case .writing(let text) = state.stage else { return }
        // The button is disabled unless the text makes a sentence, so bad input cannot reach here.
        guard let sentence = try? SentenceText(text) else { return }
        state.stage = .reading
        state.readingFailure = nil
        do {
            let opening = try await read(sentence, today)
            state.lookups = opening.lookups
            advance(ReadingDraft(opening.reading))
        } catch {
            state.readingFailure = error
            state.stage = .writing(text)
        }
    }

    func editSentence() {
        guard case .asking = state.stage else { return }
        state.stage = .writing(state.sentence)
    }

    func answerAmount(_ value: Money) { answer { $0.settingAmount(value) } }
    func answerName(_ value: String) { answer { $0.settingName(value) } }
    func answerType(_ value: TransactionType) { answer { $0.settingType(value) } }
    func answerCard(_ value: CardCandidate) { answer { $0.settingCard(value) } }
    func skipCard() { answer { $0.skippingCard() } }

    func correctDate(_ value: CalendarDay) { correct { $0.settingDate(value) } }
    func correctPaymentMethod(_ value: PaymentMethod?) { correct { $0.settingPaymentMethod(value) } }
    func correctCard(_ value: CardCandidate) { correct { $0.settingCard(value) } }
    func correctTag(_ value: TagID?) { correct { $0.settingTag(value) } }

    func save() async {
        guard case .reviewing(let draft) = state.stage else { return }
        guard let entry = draft.confirmed() else { return }
        await write(entry, from: draft)
    }

    func saveAnyway() async {
        guard case .reviewing(let draft) = state.stage else { return }
        guard let entry = draft.confirmed() else { return }
        await write(entry.withAllowingDuplicate(), from: draft)
    }

    func startAnother() {
        guard case .saved = state.stage else { return }
        let wrote = state.didWrite
        state = State()
        state.didWrite = wrote
    }

    private func answer(_ change: (ReadingDraft) -> ReadingDraft) {
        guard case .asking(let draft) = state.stage else { return }
        advance(change(draft))
    }

    private func correct(_ change: (ReadingDraft) -> ReadingDraft) {
        guard case .reviewing(let draft) = state.stage else { return }
        state.duplicate = nil
        state.writeFailure = nil
        state.stage = .reviewing(change(draft))
    }

    private func advance(_ draft: ReadingDraft) {
        guard draft.nextQuestion == nil else {
            state.stage = .asking(draft)
            return
        }
        state.stage = .reviewing(draft)
    }

    private func write(_ entry: TransactionEntry, from draft: ReadingDraft) async {
        state.stage = .saving(draft)
        state.writeFailure = nil
        state.duplicate = nil
        do {
            try await create(entry)
            state.didWrite = true
            state.stage = .saved(entry)
        } catch {
            settle(error, draft)
        }
    }

    private func settle(_ failure: WriteFailure, _ draft: ReadingDraft) {
        state.stage = .reviewing(draft)
        guard case .duplicate(let text) = failure else {
            state.writeFailure = failure
            return
        }
        state.duplicate = text
    }
}
