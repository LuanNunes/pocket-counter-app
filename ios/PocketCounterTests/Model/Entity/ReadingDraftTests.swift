import Testing

@testable import PocketCounter

@Suite("ReadingDraft")
struct ReadingDraftTests {
    private let everythingMissing = SentenceReading.fixture(
        type: nil, amount: nil, name: nil, card: .unresolved, missing: [.amount, .description, .card, .type]
    )

    @Test("a reading that misses nothing asks nothing")
    func nothingMissing() {
        #expect(ReadingDraft(.fixture()).nextQuestion == nil)
    }

    @Test("the questions come in the order the server gave")
    func queueOrder() {
        var draft = ReadingDraft(everythingMissing)
        var asked: [MissingField] = []

        while let question = draft.nextQuestion {
            asked.append(question)
            switch question {
            case .amount: draft = draft.settingAmount(Money(10))
            case .description: draft = draft.settingName("Padaria")
            case .card: draft = draft.skippingCard()
            case .type: draft = draft.settingType(.expense)
            }
        }

        #expect(asked == [.amount, .description, .card, .type])
    }

    @Test("the server's order wins over any order of our own")
    func serverOrderWins() {
        let reading = SentenceReading.fixture(type: nil, amount: nil, missing: [.type, .amount])

        #expect(ReadingDraft(reading).nextQuestion == .type)
    }

    @Test("a captured queue is asked in the order the server wrote it")
    func capturedQueue() throws {
        let dto = try WireFixtures.decode(TransactionRawResponseDTO.self, WireFixtures.Captured.ambiguousCredit)
        let draft = ReadingDraft(try SentenceReadingMapper.map(dto))

        #expect(draft.pending == [.description, .card])
        #expect(draft.nextQuestion == .description)
        #expect(draft.settingName("Gasolina").nextQuestion == .card)
    }

    @Test("answering a question out of turn does not skip the one in front")
    func outOfTurn() {
        let draft = ReadingDraft(everythingMissing).settingType(.income)

        #expect(draft.nextQuestion == .amount)
    }

    @Test("answering the amount moves on to the next question")
    func answeringDropsIt() {
        let draft = ReadingDraft(everythingMissing).settingAmount(Money(10))

        #expect(draft.nextQuestion == .description)
    }

    @Test("a blank description does not answer the question")
    func blankDescription() {
        let draft = ReadingDraft(.fixture(name: nil, missing: [.description])).settingName("   ")

        #expect(draft.nextQuestion == .description)
    }

    @Test("skipping the card leaves a credit reading with no card")
    func skippedCard() {
        let credit = Sourced<PaymentMethod>(value: .credit, source: .written)
        let reading = SentenceReading.fixture(paymentMethod: credit, card: .unresolved, missing: [.card])

        let draft = ReadingDraft(reading).skippingCard()

        #expect(draft.nextQuestion == nil)
        #expect(draft.card == nil)
        #expect(draft.paymentMethod?.value == .credit)
    }

    @Test("choosing a candidate answers the card question")
    func chosenCard() {
        let nubank = CardCandidate.fixture("k1", "Nubank Gold")
        let reading = SentenceReading.fixture(card: .ambiguous([nubank, .fixture("k2", "Nubank Black")]), missing: [.card])

        let draft = ReadingDraft(reading)
        #expect(draft.cardChoices.count == 2)
        let chosen = draft.settingCard(nubank)

        #expect(chosen.nextQuestion == nil)
        #expect(chosen.card == DraftField(value: nubank, provenance: .defined))
    }

    @Test("a resolved card is carried with the provenance the server gave it")
    func resolvedCard() {
        let nubank = CardCandidate.fixture()
        let credit = Sourced<PaymentMethod>(value: .credit, source: .inferred)
        let reading = SentenceReading.fixture(
            paymentMethod: credit, card: .resolved(Sourced(value: nubank, source: .written))
        )

        let draft = ReadingDraft(reading)

        #expect(draft.card == DraftField(value: nubank, provenance: .fromSentence))
        #expect(draft.paymentMethod == DraftField(value: .credit, provenance: .assumed))
    }

    @Test("the draft keeps what the server made of the card: a card you do not have is not no card")
    func cardReadingSurvives() {
        let nubank = CardCandidate.fixture()

        #expect(ReadingDraft(.fixture(card: .unresolved)).cardReading == .unresolved)
        #expect(ReadingDraft(.fixture(card: .notApplicable)).cardReading == .notApplicable)
        #expect(ReadingDraft(.fixture(card: .ambiguous([nubank]), missing: [.card])).cardReading == .ambiguous([nubank]))
    }

    @Test("only an ambiguous card offers choices")
    func choicesOnlyWhenAmbiguous() {
        let nubank = CardCandidate.fixture()

        #expect(ReadingDraft(.fixture(card: .ambiguous([nubank]), missing: [.card])).cardChoices == [nubank])
        #expect(ReadingDraft(.fixture(card: .unresolved)).cardChoices.isEmpty)
        #expect(ReadingDraft(.fixture(card: .notApplicable)).cardChoices.isEmpty)
    }

    @Test("skipping the card is told apart from never asking")
    func skippedIsVisible() {
        let draft = ReadingDraft(everythingMissing)

        #expect(!draft.cardSkipped)
        #expect(draft.skippingCard().cardSkipped)
    }

    @Test("the reading's provenances are kept: written and inferred")
    func provenancesFromTheServer() {
        let draft = ReadingDraft(.fixture())

        #expect(draft.amount?.provenance == .fromSentence)
        #expect(draft.date.provenance == .assumed)
    }

    @Test("an edit makes the field defined and leaves the others alone")
    func editIsDefined() {
        let original = ReadingDraft(.fixture())

        let edited = original.settingAmount(Money(99))

        #expect(edited.amount == DraftField(value: Money(99), provenance: .defined))
        #expect(edited.name == original.name)
        #expect(edited.date == original.date)
    }

    @Test("every editable field turns defined")
    func everyEdit() throws {
        let day = CalendarDay.of(2026, 10, 5)
        let edited = ReadingDraft(.fixture())
            .settingType(.income)
            .settingName("Salário")
            .settingDate(day)
            .settingPaymentMethod(.pix)

        #expect(edited.type == DraftField(value: .income, provenance: .defined))
        #expect(edited.name == DraftField(value: "Salário", provenance: .defined))
        #expect(edited.date == DraftField(value: day, provenance: .defined))
        #expect(edited.paymentMethod == DraftField(value: .pix, provenance: .defined))
    }

    @Test("clearing the payment method leaves it unset, not defined")
    func clearedPaymentMethod() {
        let credit = Sourced<PaymentMethod>(value: .credit, source: .written)

        let draft = ReadingDraft(.fixture(paymentMethod: credit)).settingPaymentMethod(nil)

        #expect(draft.paymentMethod == nil)
    }

    @Test("a suggested tag is the server's inference, not the user's words")
    func suggestedTag() {
        let draft = ReadingDraft(.fixture(tag: .of("g1")))

        #expect(draft.tag?.value == .of("g1"))
        #expect(draft.tag?.provenance == .assumed)
    }

    @Test("a chosen tag is defined, and can be cleared")
    func chosenTag() {
        let draft = ReadingDraft(.fixture(tag: .of("g1")))

        #expect(draft.settingTag(.of("g2")).tag?.value == .of("g2"))
        #expect(draft.settingTag(.of("g2")).tag?.provenance == .defined)
        #expect(draft.settingTag(nil).tag == nil)
    }

    @Test("a draft with type, amount and name confirms even while the server still lists questions")
    func confirmsWithPendingQuestions() throws {
        let draft = ReadingDraft(.fixture(missing: [.card]))

        let entry = try #require(draft.confirmed())

        #expect(entry.type == .expense)
        #expect(entry.amount == Money(250))
        #expect(entry.name == "Consulta do cachorro")
    }

    @Test("a draft missing what the server requires does not confirm", arguments: [
        SentenceReading.fixture(type: nil),
        SentenceReading.fixture(amount: nil),
        SentenceReading.fixture(name: nil),
    ])
    func doesNotConfirm(reading: SentenceReading) {
        #expect(ReadingDraft(reading).confirmed() == nil)
    }

    @Test("a name of only whitespace leaves the draft unconfirmable")
    func blankNameUnconfirms() {
        #expect(ReadingDraft(.fixture()).settingName("   ").confirmed() == nil)
    }

    // A card the sentence named carries .fromSentence, not .defined.
    @Test("a card the sentence named travels too, not only one the user picked")
    func resolvedCardTravels() throws {
        let draft = ReadingDraft(
            .fixture(
                paymentMethod: Sourced(value: .credit, source: .written),
                card: .resolved(Sourced(value: .fixture("k1", "Nubank"), source: .written))
            )
        )

        #expect(draft.card?.provenance == .fromSentence)
        #expect(try #require(draft.confirmed()).card == CardID(rawValue: "k1"))
    }

    @Test("naming a card is naming credit")
    func cardImpliesCredit() {
        let draft = ReadingDraft(.fixture()).settingCard(.fixture())

        #expect(draft.paymentMethod?.value == .credit)
        #expect(draft.confirmed()?.card == CardID(rawValue: "k1"))
    }

    @Test("leaving credit drops the card, so no row carries one without it")
    func nonCreditDropsCard() {
        let draft = ReadingDraft(.fixture()).settingCard(.fixture()).settingPaymentMethod(.pix)

        #expect(draft.card == nil)
        #expect(draft.confirmed()?.card == nil)
    }

    @Test("a card dropped by leaving credit is not asked about again")
    func droppedCardIsNotAsked() {
        let draft = ReadingDraft(.fixture(missing: [.card])).settingCard(.fixture())

        #expect(draft.settingPaymentMethod(.pix).nextQuestion == nil)
    }

    @Test("an unknown payment method keeps the card question open")
    func unknownMethodKeepsCardQuestion() {
        let draft = ReadingDraft(.fixture(missing: [.card])).settingCard(.fixture())

        #expect(draft.settingPaymentMethod(nil).nextQuestion == .card)
    }

    @Test("choosing credit again keeps the card")
    func creditKeepsCard() {
        let draft = ReadingDraft(.fixture()).settingCard(.fixture()).settingPaymentMethod(.credit)

        #expect(draft.card != nil)
    }

    @Test("a name the entry refuses on length does not confirm")
    func overlongNameDoesNotConfirm() {
        let name = String(repeating: "a", count: TransactionEntry.maxNameUTF16Units + 1)

        #expect(ReadingDraft(.fixture(name: Sourced(value: name, source: .written))).confirmed() == nil)
    }
}
