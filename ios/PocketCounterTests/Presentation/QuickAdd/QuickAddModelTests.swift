import Foundation
import Testing

@testable import PocketCounter

@MainActor
@Suite("QuickAddModel")
struct QuickAddModelTests {
    private let day = CalendarDay.of(2026, 10, 7)

    private func model(
        opening: Result<SentenceOpening, ReadingFailure> = .success(
            SentenceOpening(reading: .fixture(), lookups: .fixture())
        ),
        writes: [Result<Void, WriteFailure>] = [.success(())],
        log: CreatedEntryLog = CreatedEntryLog(),
        reads: ReadLog = ReadLog()
    ) -> QuickAddModel {
        let remaining = ScriptedWrites(writes)
        return QuickAddModel(
            read: { sentence, today throws(ReadingFailure) in
                reads.record(sentence, today)
                return try opening.get()
            },
            create: { entry throws(WriteFailure) in
                log.record(entry)
                try remaining.next().get()
            },
            today: day
        )
    }

    @Test("a sentence the server reads whole goes straight to the review")
    func straightToReview() async {
        let model = self.model()

        model.type("gastei 250 numa consulta")
        await model.send()

        guard case .reviewing(let draft) = model.state.stage else { return #expect(Bool(false)) }
        #expect(draft.amount?.value == Money(250))
    }

    @Test("a blank answer to the description question changes nothing")
    func blankNameAnswer() async {
        let reading = SentenceReading.fixture(name: nil, missing: [.description])
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())))

        model.type("paguei 250")
        await model.send()
        model.answerName("   ")

        guard case .asking(let draft) = model.state.stage else { return #expect(Bool(false)) }
        #expect(draft.nextQuestion == .description)
        #expect(draft.confirmed() == nil)
    }

    @Test("the type question is picked, not typed, and answering it opens the review")
    func answersType() async {
        let reading = SentenceReading.fixture(type: nil, missing: [.type])
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())))

        model.type("250 numa consulta")
        await model.send()

        guard case .asking = model.state.stage else { return #expect(Bool(false)) }
        #expect(QuickAddQuestionKind(.type) == .picked(.type))
        model.answerType(.income)
        guard case .reviewing(let draft) = model.state.stage else { return #expect(Bool(false)) }
        #expect(draft.type?.value == .income)
    }

    @Test("correcting a field in the review clears a duplicate warning")
    func correctionClearsDuplicate() async {
        let model = self.model(writes: [.failure(.duplicate("Já existe")), .success(())])

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()
        #expect(model.state.duplicate == "Já existe")
        model.correctDate(.of(2026, 10, 6))

        #expect(model.state.duplicate == nil)
    }

    @Test("a question is asked, answered, and then the review opens")
    func answersOneQuestion() async {
        let reading = SentenceReading.fixture(amount: nil, missing: [.amount])
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())))

        model.type("paguei uma consulta")
        await model.send()

        guard case .asking(let asked) = model.state.stage else { return #expect(Bool(false)) }
        #expect(asked.nextQuestion == .amount)
        model.answerAmount(Money(250))
        guard case .reviewing(let draft) = model.state.stage else { return #expect(Bool(false)) }
        #expect(draft.amount?.provenance == .defined)
    }

    @Test("the card question is the only one that can be skipped")
    func skipsCard() async {
        let reading = SentenceReading.fixture(card: .unresolved, missing: [.card])
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())))

        model.type("almoço 68 no cartão")
        await model.send()
        model.skipCard()

        guard case .reviewing = model.state.stage else { return #expect(Bool(false)) }
    }

    @Test("editing the sentence returns to the text the user wrote")
    func editsTheSentence() async {
        let reading = SentenceReading.fixture(amount: nil, missing: [.amount])
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())))

        model.type("paguei uma consulta")
        await model.send()
        model.editSentence()

        #expect(model.state.stage == .writing("paguei uma consulta"))
    }

    @Test("a reading that fails keeps the sentence and says why")
    func readingFails() async {
        let model = self.model(opening: .failure(.tooManyRequests))

        model.type("gastei 250 numa consulta")
        await model.send()

        #expect(model.state.stage == .writing("gastei 250 numa consulta"))
        #expect(model.state.readingFailure == .tooManyRequests)
    }

    @Test("confirming writes the row and shows the receipt")
    func writes() async {
        let log = CreatedEntryLog()
        let model = self.model(log: log)

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()

        #expect(model.state.stage.isSaved)
        #expect(log.entries.count == 1)
        #expect(log.entries.first?.allowDuplicate == false)
    }

    @Test("the receipt carries the entry that was written")
    func receiptCarriesEntry() async throws {
        let model = self.model(log: CreatedEntryLog())

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()

        guard case .saved(let entry) = model.state.stage else {
            Issue.record("not saved")
            return
        }
        #expect(entry.amount == Money(250))
    }

    @Test("a duplicate is a question, and insisting writes the same row with the flag")
    func duplicateThenInsist() async {
        let log = CreatedEntryLog()
        let model = self.model(
            writes: [.failure(.duplicate("Já existe Consulta do cachorro")), .success(())], log: log
        )

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()

        guard case .reviewing = model.state.stage else { return #expect(Bool(false)) }
        #expect(model.state.duplicate == "Já existe Consulta do cachorro")
        await model.saveAnyway()
        #expect(model.state.stage.isSaved)
        #expect(model.state.duplicate == nil)
        #expect(model.state.writeFailure == nil)
        #expect(log.entries.map(\.allowDuplicate) == [false, true])
    }

    @Test("a successful write is remembered past the receipt and past Lançar outro")
    func remembersWriting() async {
        let model = self.model()

        model.type("gastei 250 numa consulta")
        await model.send()
        #expect(model.state.didWrite == false)
        await model.save()
        #expect(model.state.didWrite)
        model.startAnother()
        #expect(model.state.didWrite)
        #expect(model.state.stage == .writing(""))
        #expect(model.state.sentence == "")
    }

    @Test("a write that fails for any other reason returns to the review with a notice")
    func writeFails() async {
        let model = self.model(writes: [.failure(.unreachable)])

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()

        guard case .reviewing = model.state.stage else { return #expect(Bool(false)) }
        #expect(model.state.writeFailure == .unreachable)
        #expect(model.state.duplicate == nil)
        #expect(model.state.didWrite == false)
    }

    @Test("correcting a field clears a write failure notice")
    func correctionClearsWriteFailure() async {
        let model = self.model(writes: [.failure(.unreachable)])

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()
        #expect(model.state.writeFailure == .unreachable)
        model.correctDate(.of(2026, 10, 6))

        #expect(model.state.writeFailure == nil)
    }

    @Test("insisting on a duplicate that then fails leaves only the new failure")
    func insistFails() async {
        let model = self.model(writes: [.failure(.duplicate("Já existe")), .failure(.unreachable)])

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()
        await model.saveAnyway()

        guard case .reviewing = model.state.stage else { return #expect(Bool(false)) }
        #expect(model.state.duplicate == nil)
        #expect(model.state.writeFailure == .unreachable)
        #expect(model.state.didWrite == false)
    }

    @Test("a retry after a failure clears the failure when it succeeds")
    func retryClearsFailure() async {
        let model = self.model(writes: [.failure(.unreachable), .success(())])

        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()
        await model.save()

        #expect(model.state.stage.isSaved)
        #expect(model.state.writeFailure == nil)
    }

    @Test("the reading receives the sentence and today, and its lookups reach the state")
    func readReceivesArguments() async throws {
        let reads = ReadLog()
        let lookups = LookupSet.fixture(tags: [.fixture("t1")])
        let opening = SentenceOpening(reading: .fixture(), lookups: lookups)
        let model = self.model(opening: .success(opening), reads: reads)

        model.type("gastei 250 numa consulta")
        await model.send()

        let expected = try SentenceText("gastei 250 numa consulta")
        #expect(reads.calls.map(\.0) == [expected])
        #expect(reads.calls.map(\.1) == [day])
        #expect(model.state.lookups == lookups)
    }

    @Test("a draft the server called complete but that cannot become an entry cannot be confirmed")
    func cannotConfirm() async {
        let reading = SentenceReading.fixture(amount: nil, missing: [])
        let log = CreatedEntryLog()
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())), log: log)

        model.type("paguei uma consulta")
        await model.send()
        #expect(model.state.canConfirm == false)
        await model.save()

        guard case .reviewing = model.state.stage else { return #expect(Bool(false)) }
        #expect(log.entries.isEmpty)
    }

    @Test("a complete draft can be confirmed")
    func canConfirm() async {
        let model = self.model()

        model.type("gastei 250 numa consulta")
        await model.send()

        #expect(model.state.canConfirm)
    }

    @Test("the question kinds", arguments: [
        (MissingField.amount, QuickAddQuestionKind.typed(.amount)),
        (.description, .typed(.description)),
        (.type, .picked(.type)),
        (.card, .picked(.card)),
    ])
    func questionKinds(field: MissingField, kind: QuickAddQuestionKind) {
        #expect(QuickAddQuestionKind(field) == kind)
    }

    @Test("answering a card picks credit and moves to the review")
    func answersCard() async {
        let nubank = CardCandidate.fixture()
        let reading = SentenceReading.fixture(card: .ambiguous([nubank, .fixture("k2", "Inter")]), missing: [.card])
        let model = self.model(opening: .success(SentenceOpening(reading: reading, lookups: .fixture())))

        model.type("almoço 68 no cartão")
        await model.send()
        model.answerCard(nubank)

        guard case .reviewing(let draft) = model.state.stage else { return #expect(Bool(false)) }
        #expect(draft.card?.value == nubank)
        #expect(draft.paymentMethod?.value == .credit)
    }

    @Test("the review corrects the card, the payment method and the tag")
    func correctsFields() async {
        let nubank = CardCandidate.fixture()
        let model = self.model()

        model.type("gastei 250 numa consulta")
        await model.send()
        model.correctCard(nubank)
        model.correctTag(.of("t1"))
        guard case .reviewing(let credit) = model.state.stage else { return #expect(Bool(false)) }
        #expect(credit.card?.value == nubank)
        #expect(credit.tag?.value == .of("t1"))
        model.correctPaymentMethod(.pix)

        guard case .reviewing(let pix) = model.state.stage else { return #expect(Bool(false)) }
        #expect(pix.paymentMethod?.value == .pix)
        #expect(pix.card == nil)
    }

    @Test("an action from the wrong stage changes nothing")
    func wrongStageIsNoOp() async {
        let log = CreatedEntryLog()
        let reads = ReadLog()
        let model = self.model(log: log, reads: reads)

        model.answerAmount(Money(1))
        model.answerName("x")
        model.answerType(.income)
        model.answerCard(.fixture())
        model.skipCard()
        model.correctDate(.of(2026, 1, 1))
        model.correctPaymentMethod(.pix)
        model.correctCard(.fixture())
        model.correctTag(.of("t1"))
        model.editSentence()
        model.startAnother()
        await model.save()
        await model.saveAnyway()
        #expect(model.state == QuickAddModel.State())

        model.type("gastei 250 numa consulta")
        await model.send()
        let reviewing = model.state
        model.type("outra coisa")
        model.answerAmount(Money(1))
        model.editSentence()
        model.startAnother()
        await model.send()
        #expect(model.state == reviewing)
        #expect(reads.calls.count == 1)

        await model.save()
        let saved = model.state
        model.type("outra coisa")
        model.correctDate(.of(2026, 1, 1))
        model.editSentence()
        await model.save()
        await model.saveAnyway()
        #expect(model.state == saved)
        #expect(log.entries.count == 1)
    }

    private func settle() async {
        for _ in 0..<50 { await Task.yield() }
    }

    private func gatedModel(duplicateFirst: Bool, gate: WriteGate, count: WriteCount) -> QuickAddModel {
        QuickAddModel(
            read: { _, _ throws(ReadingFailure) in SentenceOpening(reading: .fixture(), lookups: .fixture()) },
            create: { _ throws(WriteFailure) in
                let call = count.increment()
                if duplicateFirst && call == 1 { throw .duplicate("Já existe") }
                await gate.wait()
            },
            today: day
        )
    }

    @Test("pressing Lançar twice while the write is in flight writes once")
    func doubleTap() async {
        let gate = WriteGate()
        let count = WriteCount()
        let model = gatedModel(duplicateFirst: false, gate: gate, count: count)
        model.type("gastei 250 numa consulta")
        await model.send()

        async let first: Void = model.save()
        await count.reached(1)
        async let second: Void = model.save()
        await settle()
        gate.open()
        await first
        await second

        #expect(count.value == 1)
        #expect(model.state.stage.isSaved)
    }

    @Test("pressing Lançar mesmo assim twice while the write is in flight writes once more")
    func doubleTapInsist() async {
        let gate = WriteGate()
        let count = WriteCount()
        let model = gatedModel(duplicateFirst: true, gate: gate, count: count)
        model.type("gastei 250 numa consulta")
        await model.send()
        await model.save()
        #expect(model.state.duplicate == "Já existe")

        async let first: Void = model.saveAnyway()
        await count.reached(2)
        async let second: Void = model.saveAnyway()
        await settle()
        gate.open()
        await first
        await second

        #expect(count.value == 2)
        #expect(model.state.stage.isSaved)
    }
}

final class ReadLog: @unchecked Sendable {
    private let lock = NSLock()
    private var recorded: [(SentenceText, CalendarDay)] = []

    var calls: [(SentenceText, CalendarDay)] { lock.withLock { recorded } }

    func record(_ sentence: SentenceText, _ day: CalendarDay) {
        lock.withLock { recorded.append((sentence, day)) }
    }
}

final class WriteCount: @unchecked Sendable {
    private let lock = NSLock()
    private var count = 0

    var value: Int { lock.withLock { count } }

    func reached(_ target: Int) async {
        for _ in 0..<1000 {
            guard value < target else { return }
            await Task.yield()
        }
    }

    @discardableResult
    func increment() -> Int { lock.withLock { count += 1; return count } }
}

final class WriteGate: @unchecked Sendable {
    private let lock = NSLock()
    private var opened = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        await withCheckedContinuation { continuation in
            let resume = lock.withLock { () -> Bool in
                guard !opened else { return true }
                waiters.append(continuation)
                return false
            }
            if resume { continuation.resume() }
        }
    }

    func open() {
        let pending = lock.withLock { () -> [CheckedContinuation<Void, Never>] in
            opened = true
            defer { waiters = [] }
            return waiters
        }
        pending.forEach { $0.resume() }
    }
}

final class ScriptedWrites: @unchecked Sendable {
    private let lock = NSLock()
    private var remaining: [Result<Void, WriteFailure>]

    init(_ remaining: [Result<Void, WriteFailure>]) { self.remaining = remaining }

    func next() -> Result<Void, WriteFailure> {
        lock.withLock { remaining.count > 1 ? remaining.removeFirst() : remaining[0] }
    }
}

extension QuickAddStage {
    fileprivate var isSaved: Bool {
        guard case .saved = self else { return false }
        return true
    }
}
