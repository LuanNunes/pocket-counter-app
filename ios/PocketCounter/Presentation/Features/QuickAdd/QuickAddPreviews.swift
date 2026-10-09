#if DEBUG
import SwiftUI

enum QuickAddPreviewData {
    static let today: CalendarDay = {
        do { return try CalendarDay(year: 2026, month: 10, day: 7) } catch { fatalError("preview day: \(error)") }
    }()

    static let lookups = LookupSet(
        categories: [],
        tags: [
            Tag(id: TagID(rawValue: "g1"), name: "Saúde", kind: .expense, color: 0xFF34_C759),
            Tag(id: TagID(rawValue: "g2"), name: "Mercado", kind: .expense, color: 0xFFFF_9500),
        ],
        cards: [
            CreditCard(id: CardID(rawValue: "k1"), name: "Cartão Nubank", brand: nil, closingDay: nil, color: nil),
            CreditCard(id: CardID(rawValue: "k2"), name: "Itaú Platinum Black Internacional", brand: nil, closingDay: nil, color: nil),
        ]
    )

    static func reading(
        amount: Bool = true, method: PaymentMethod? = .pix, card: CardReading = .notApplicable,
        missing: [MissingField] = []
    ) -> SentenceReading {
        SentenceReading(
            type: Sourced(value: .expense, source: .written),
            amount: amount ? Sourced(value: Money(250), source: .written) : nil,
            date: Sourced(value: today, source: .inferred),
            name: Sourced(value: "Consulta do cachorro", source: .written),
            paymentMethod: method.map { Sourced(value: $0, source: .written) },
            card: card, tag: TagID(rawValue: "g1"), missing: missing
        )
    }

    static func state(_ stage: QuickAddStage) -> QuickAddModel.State {
        var state = QuickAddModel.State()
        state.stage = stage
        state.lookups = lookups
        state.sentence = "paguei 250 em uma consulta do cachorro"
        return state
    }

    static var entry: TransactionEntry? {
        ReadingDraft(reading()).confirmed()
    }
}

private struct QuickAddPreviewHost: View {
    let state: QuickAddModel.State
    @State private var answer = ""
    @State private var opened: QuickAddReviewRow.Field?

    var body: some View {
        Color.clear.sheet(isPresented: .constant(true)) {
            QuickAddContent(
                state: state, today: QuickAddPreviewData.today,
                answer: $answer, opened: $opened, onAction: { _ in }
            )
            .presentationDetents([.large])
        }
    }
}

#Preview("Escrevendo") {
    QuickAddPreviewHost(state: QuickAddPreviewData.state(.writing("paguei 250 em uma consulta")))
}

#Preview("Pergunta de valor") {
    let draft = ReadingDraft(QuickAddPreviewData.reading(amount: false, missing: [.amount]))
    QuickAddPreviewHost(state: QuickAddPreviewData.state(.asking(draft)))
}

#Preview("Pergunta de cartão") {
    let choices = QuickAddPreviewData.lookups.cards.map { CardCandidate(id: $0.id, name: $0.name) }
    let draft = ReadingDraft(QuickAddPreviewData.reading(
        method: .credit, card: .ambiguous(choices), missing: [.card]
    ))
    QuickAddPreviewHost(state: QuickAddPreviewData.state(.asking(draft)))
}

#Preview("Revisão") {
    QuickAddPreviewHost(state: QuickAddPreviewData.state(.reviewing(ReadingDraft(QuickAddPreviewData.reading()))))
}

#Preview("Recibo") {
    if let entry = QuickAddPreviewData.entry {
        QuickAddPreviewHost(state: QuickAddPreviewData.state(.saved(entry)))
    }
}
#endif
