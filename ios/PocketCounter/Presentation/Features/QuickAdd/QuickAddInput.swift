import SwiftUI

/// The text surface: the sentence, and the description when the server asks for one.
struct QuickAddField: View {
    let placeholder: String
    let accessibilityLabel: String
    @Binding var text: String
    var lines: ClosedRange<Int> = 3...8
    var submitLabel = SubmitLabel.send
    var onSubmit: () -> Void = {}

    @FocusState private var isFocused: Bool

    var body: some View {
        TextField(
            "", text: $text,
            prompt: Text(verbatim: placeholder).foregroundStyle(PocketColor.labelTertiary),
            axis: .vertical
        )
        .pocketFont(PocketFont.entry)
        .foregroundStyle(PocketColor.label)
        .lineLimit(lines)
        .submitLabel(submitLabel)
        .onSubmit(onSubmit)
        .focused($isFocused)
        .accessibilityLabel(accessibilityLabel)
        .padding(PocketMetrics.entryPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(PocketColor.cell, in: .rect(cornerRadius: PocketMetrics.entryRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: PocketMetrics.entryRadius, style: .continuous)
                .strokeBorder(PocketColor.tint.opacity(0.35), lineWidth: PocketMetrics.entryRing)
        }
        .focusedAfterSettling($isFocused)
    }
}

extension View {
    /// Focusing at once makes the keyboard and the sheet animate together.
    func focusedAfterSettling(_ focus: FocusState<Bool>.Binding) -> some View {
        task {
            do { try await Task.sleep(for: .milliseconds(420)) } catch { return }
            focus.wrappedValue = true
        }
    }
}

/// The `writing` and `reading` stages.
struct QuickAddWriting: View {
    let text: String
    let notice: PocketNotice?
    let isReading: Bool
    let onAction: (QuickAddAction) -> Void

    var body: some View {
        VStack(spacing: 12) {
            if let notice {
                PocketNoticeCard(notice: notice)
            }
            QuickAddField(
                placeholder: QuickAddCopy.placeholder,
                accessibilityLabel: QuickAddCopy.sentenceField,
                text: Binding(get: { text }, set: { onAction(.type($0)) }),
                onSubmit: submit
            )
            .disabled(isReading)
            .padding(.horizontal, PocketMetrics.screenMargin)
        }
    }

    private func submit() {
        guard QuickAddAnswer.isSendable(text) else { return }
        onAction(.send)
    }
}
