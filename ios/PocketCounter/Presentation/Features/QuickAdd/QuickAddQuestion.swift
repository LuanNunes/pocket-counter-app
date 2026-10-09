import SwiftUI

/// The `asking` stage: what the server understood, the question, and the control that answers it.
struct QuickAddQuestion: View {
    let draft: ReadingDraft
    let field: MissingField
    @Binding var answer: String
    let onAction: (QuickAddAction) -> Void

    @AccessibilityFocusState private var headingFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            QuickAddUnderstoodCard(items: QuickAddReviewRows.understood(from: draft))
                .padding(.horizontal, PocketMetrics.screenMargin)
            Text(verbatim: QuickAddCopy.question(field))
                .pocketFont(PocketFont.question)
                .foregroundStyle(PocketColor.label)
                .accessibilityAddTraits(.isHeader)
                .accessibilityFocused($headingFocused)
                .padding(PocketMetrics.questionPadding)
                .padding(.horizontal, PocketMetrics.screenMargin)
            control
        }
        .task { headingFocused = true }
    }

    @ViewBuilder
    private var control: some View {
        switch field {
        case .amount:
            QuickAddAmountField(text: $answer, onSubmit: submitAmount)
                .padding(.horizontal, PocketMetrics.screenMargin)
        case .description:
            QuickAddField(
                placeholder: QuickAddCopy.namePlaceholder,
                accessibilityLabel: QuickAddCopy.question(.description),
                text: $answer, lines: 1...3, submitLabel: .done, onSubmit: submitName
            )
            .padding(.horizontal, PocketMetrics.screenMargin)
        case .type:
            PocketListSection {
                pickedRow(QuickAddCopy.expense) { onAction(.answerType(.expense)) }
                PocketRowSeparator()
                pickedRow(QuickAddCopy.income) { onAction(.answerType(.income)) }
            }
        case .card:
            PocketListSection {
                ForEach(draft.cardChoices, id: \.id) { card in
                    pickedRow(card.name) { onAction(.answerCard(card)) }
                    PocketRowSeparator()
                }
                skipRow
            }
        }
    }

    private func pickedRow(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            PocketRow(title: title) {
                Image(systemName: "chevron.right")
                    .pocketFont(PocketFont.groupChevron)
                    .foregroundStyle(PocketColor.labelTertiary)
                    .accessibilityHidden(true)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    private var skipRow: some View {
        Button { onAction(.skipCard) } label: {
            PocketRow(title: QuickAddCopy.skipCard)
                .foregroundStyle(PocketColor.labelSecondary)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    private func submitAmount() {
        guard let money = QuickAddAnswer.amount(answer) else { return }
        onAction(.answerAmount(money))
    }

    private func submitName() {
        guard let name = QuickAddAnswer.name(answer) else { return }
        onAction(.answerName(name))
    }
}

private struct QuickAddUnderstoodCard: View {
    let items: [QuickAddUnderstood]

    var body: some View {
        QuickAddFlowLayout(hSpacing: PocketMetrics.readCardSpacing, vSpacing: PocketMetrics.readCardSpacing) {
            Text(verbatim: QuickAddCopy.understood)
                .pocketFont(PocketFont.eyebrow)
                .textCase(.uppercase)
                .foregroundStyle(PocketColor.labelSecondary)
            ForEach(items) { item in
                QuickAddChipLabel(
                    text: item.text, look: item.isIncome ? .income : .plain,
                    leading: item.symbol.map(QuickAddChipLabel.Leading.symbol) ?? .none
                )
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(PocketMetrics.readCardPadding)
        .background(PocketColor.cell, in: .rect(cornerRadius: PocketMetrics.readCardRadius, style: .continuous))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(QuickAddCopy.understood)
        .accessibilityValue(items.map(\.spoken).joined(separator: ", "))
    }
}

private struct QuickAddAmountField: View {
    @Binding var text: String
    let onSubmit: () -> Void

    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: PocketMetrics.amountFieldSpacing) {
            Text(verbatim: QuickAddCopy.currencyPrefix)
                .pocketFont(PocketFont.notice)
                .foregroundStyle(PocketColor.labelSecondary)
                .accessibilityHidden(true)
            TextField(
                "", text: $text,
                prompt: Text(verbatim: QuickAddCopy.amountPlaceholder).foregroundStyle(PocketColor.labelTertiary)
            )
            .pocketFont(PocketFont.detailAmount, tabularFigures: true)
            .foregroundStyle(PocketColor.label)
            .keyboardType(.decimalPad)
            .minimumScaleFactor(0.5)
            .lineLimit(1)
            .focused($isFocused)
            .accessibilityLabel(QuickAddCopy.amountField)
            .onSubmit(onSubmit)
        }
        .padding(PocketMetrics.amountFieldPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PocketColor.cell, in: .rect(cornerRadius: PocketMetrics.amountFieldRadius, style: .continuous))
        .focusedAfterSettling($isFocused)
    }
}
