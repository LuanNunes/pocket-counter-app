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
        log: CreatedEntryLog = CreatedEntryLog()
    ) -> QuickAddModel {
        let remaining = Replies(writes)
        return QuickAddModel(
            read: { _, _ throws(ReadingFailure) in try opening.get() },
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

        guard case .asking(let asked) = model.state.stage else { return #expect(Bool(false)) }
        #expect(QuickAddQuestionKind(.type, choices: asked.cardChoices) == .picked(.type))
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

        #expect(model.state.stage == .saved)
        #expect(log.entries.count == 1)
        #expect(log.entries.first?.allowDuplicate == false)
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
        #expect(model.state.stage == .saved)
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
    }
}

final class Replies: @unchecked Sendable {
    private let lock = NSLock()
    private var remaining: [Result<Void, WriteFailure>]

    init(_ remaining: [Result<Void, WriteFailure>]) { self.remaining = remaining }

    func next() -> Result<Void, WriteFailure> {
        lock.withLock { remaining.count > 1 ? remaining.removeFirst() : remaining[0] }
    }
}
