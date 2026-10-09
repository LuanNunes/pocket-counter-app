import SwiftUI

/// The one place that holds the model; everything below takes values and closures.
struct QuickAddSheet: View {
    let model: QuickAddModel
    let today: CalendarDay
    let onFinish: () -> Void

    @State private var answer = ""
    @State private var opened: QuickAddReviewRow.Field?
    @State private var receipt: TransactionEntry?
    @State private var detent = PresentationDetent.fraction(Self.keyboardFraction)
    @Environment(\.dynamicTypeSize) private var typeSize

    private static let keyboardFraction = 0.7

    var body: some View {
        QuickAddContent(
            state: model.state, receipt: receipt, today: today,
            answer: $answer, opened: $opened, onAction: perform
        )
        .presentationDetents(detents, selection: selection)
        .presentationDragIndicator(.visible)
        .interactiveDismissDisabled(isBusy)
        .onChange(of: model.state.stage) { _, stage in remember(stage) }
        .onChange(of: hasKeyboard) { settle() }
    }

    private func perform(_ action: QuickAddAction) {
        switch action {
        case .type(let text): model.type(text)
        case .send: Task { await model.send() }
        case .editSentence: model.editSentence()
        case .answerAmount(let money): model.answerAmount(money)
        case .answerName(let name): model.answerName(name)
        case .answerType(let type): model.answerType(type)
        case .answerCard(let card): model.answerCard(card)
        case .skipCard: model.skipCard()
        case .correct(let change): correct(change)
        case .save: Task { await model.save() }
        case .saveAnyway: Task { await model.saveAnyway() }
        case .startAnother: model.startAnother()
        case .finish: onFinish()
        }
    }

    private func correct(_ change: QuickAddChip.Change) {
        switch change {
        case .date(let day): model.correctDate(day)
        case .paymentMethod(let method): model.correctPaymentMethod(method)
        case .card(let card): model.correctCard(card)
        case .tag(let tag): model.correctTag(tag)
        }
    }

    private var isBusy: Bool {
        switch model.state.stage {
        case .reading, .saving: true
        case .writing, .asking, .reviewing, .saved: false
        }
    }

    private var hasKeyboard: Bool {
        switch model.state.stage {
        case .writing, .reading, .asking: true
        case .reviewing, .saving, .saved: false
        }
    }

    private var detents: Set<PresentationDetent> {
        guard !typeSize.isAccessibilitySize else { return [.large] }
        return hasKeyboard ? [.fraction(Self.keyboardFraction), .large] : [.medium, .large]
    }

    private var selection: Binding<PresentationDetent> {
        Binding(get: { detents.contains(detent) ? detent : .large }, set: { detent = $0 })
    }

    private func remember(_ stage: QuickAddStage) {
        guard case .saving(let draft) = stage else { return }
        receipt = draft.confirmed()
    }

    private func settle() {
        opened = nil
        detent = hasKeyboard ? .fraction(Self.keyboardFraction) : .medium
    }
}
