import SwiftUI

/// The whole sheet for a given state. Owns no model: every change leaves through `onAction`.
struct QuickAddContent: View {
    let state: QuickAddModel.State
    let receipt: TransactionEntry?
    let today: CalendarDay
    @Binding var answer: String
    @Binding var opened: QuickAddReviewRow.Field?
    let onAction: (QuickAddAction) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showsSpinner = false

    private static let topID = "top"

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 0) {
                        stage
                            .id(phase)
                            .transition(.opacity)
                    }
                    .padding(.vertical, PocketMetrics.screenMargin)
                    .id(Self.topID)
                }
                .scrollEdgeEffectStyle(.soft, for: .bottom)
                .scrollDismissesKeyboard(.interactively)
                .onChange(of: opened) { _, field in reveal(field, with: proxy) }
                .onChange(of: state.duplicate) { _, text in
                    guard text != nil else { return }
                    proxy.scrollTo(Self.topID, anchor: .top)
                }
                .onChange(of: state.writeFailure) { _, failure in
                    guard failure != nil else { return }
                    proxy.scrollTo(Self.topID, anchor: .top)
                }
            }
            .background(PocketColor.background)
            .navigationTitle(QuickAddCopy.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(role: .close) { onAction(.finish) }
                        .disabled(isBusy)
                }
            }
            .safeAreaInset(edge: .bottom) {
                QuickAddFooter(items: footerItems)
            }
        }
        .animation(reduceMotion ? PocketMotion.reduced : PocketMotion.standard, value: phase)
        .graced(isActive: isBusy, elapsed: $showsSpinner)
        .onChange(of: phase) { answer = "" }
    }

    // MARK: Stages

    @ViewBuilder
    private var stage: some View {
        switch state.stage {
        case .writing(let text):
            QuickAddWriting(text: text, notice: readingNotice, isReading: false, onAction: onAction)
        case .reading:
            QuickAddWriting(text: state.sentence, notice: nil, isReading: true, onAction: onAction)
        case .asking(let draft):
            asking(draft)
        case .reviewing(let draft), .saving(let draft):
            reviewing(draft)
        case .saved:
            savedReceipt
        }
    }

    @ViewBuilder
    private func asking(_ draft: ReadingDraft) -> some View {
        if let field = draft.nextQuestion {
            QuickAddQuestion(draft: draft, field: field, answer: $answer, onAction: onAction)
        }
    }

    @ViewBuilder
    private var savedReceipt: some View {
        if let receipt {
            QuickAddReceipt(entry: receipt)
        }
    }

    private func reviewing(_ draft: ReadingDraft) -> some View {
        VStack(spacing: 12) {
            if let writeNotice {
                PocketNoticeCard(notice: writeNotice.notice, action: writeNotice.action)
            }
            if let entry = draft.confirmed() {
                QuickAddIdentity(
                    entry: entry, status: QuickAddCopy.kindName(entry.type),
                    spokenStatus: QuickAddCopy.kindName(entry.type), takesFocus: writeNotice == nil
                )
            }
            QuickAddReviewList(
                rows: QuickAddReviewRows.rows(from: draft, lookups: state.lookups, today: today),
                chips: { QuickAddReviewRows.chips(for: $0, from: draft, lookups: state.lookups, today: today) },
                opened: $opened,
                onChange: { onAction(.correct($0)) }
            )
            .disabled(isBusy)
            Text(verbatim: QuickAddCopy.reviewNote)
                .pocketFont(PocketFont.caption)
                .foregroundStyle(PocketColor.labelSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(PocketMetrics.footNotePadding)
        }
    }

    // MARK: Notices

    private struct WriteNotice: Equatable {
        let notice: PocketNotice
        let action: PocketInlineMessage.Action?

        static func == (lhs: WriteNotice, rhs: WriteNotice) -> Bool { lhs.notice == rhs.notice }
    }

    private var readingNotice: PocketNotice? {
        state.readingFailure.flatMap(ReadingFailureMessage.message(for:))
    }

    private var writeNotice: WriteNotice? {
        if let text = state.duplicate {
            return WriteFailureMessage.message(for: .duplicate(text), subject: .saving).map {
                WriteNotice(notice: $0, action: .init(title: QuickAddCopy.saveAnyway) { onAction(.saveAnyway) })
            }
        }
        guard let failure = state.writeFailure,
              let notice = WriteFailureMessage.message(for: failure, subject: .saving)
        else { return nil }
        return WriteNotice(notice: notice, action: retry(failure))
    }

    private func retry(_ failure: WriteFailure) -> PocketInlineMessage.Action? {
        switch failure {
        case .unreachable, .server, .authenticationUnavailable:
            .init(title: QuickAddCopy.retry) { onAction(.save) }
        case .sessionExpired, .rejected, .duplicate, .vanished:
            nil
        }
    }

    // MARK: Chrome

    private var isBusy: Bool {
        switch state.stage {
        case .reading, .saving: true
        case .writing, .asking, .reviewing, .saved: false
        }
    }

    private var phase: String {
        switch state.stage {
        case .writing, .reading: "input"
        case .asking(let draft): "asking-\(String(describing: draft.nextQuestion))"
        case .reviewing, .saving: "review"
        case .saved: "saved"
        }
    }

    private func reveal(_ field: QuickAddReviewRow.Field?, with proxy: ScrollViewProxy) {
        guard let field else { return }
        Task {
            await Task.yield()
            withAnimation(reduceMotion ? PocketMotion.reduced : PocketMotion.standard) {
                proxy.scrollTo(QuickAddReviewList.stripID(field), anchor: .bottom)
            }
        }
    }

    // MARK: Footer

    private var footerItems: [QuickAddFooterItem] {
        switch state.stage {
        case .writing(let text):
            [send(disabled: !QuickAddAnswer.isSendable(text), loading: false)]
        case .reading:
            [send(disabled: true, loading: showsSpinner)]
        case .asking(let draft):
            askingItems(draft)
        case .reviewing:
            [save(disabled: !state.canConfirm, loading: false)]
        case .saving:
            [save(disabled: true, loading: showsSpinner)]
        case .saved:
            [
                QuickAddFooterItem(id: "another", title: QuickAddCopy.another, role: .secondary) { onAction(.startAnother) },
                QuickAddFooterItem(id: "done", title: QuickAddCopy.done, role: .prominent) { onAction(.finish) },
            ]
        }
    }

    private func send(disabled: Bool, loading: Bool) -> QuickAddFooterItem {
        QuickAddFooterItem(
            id: "primary", title: QuickAddCopy.send, role: .prominent, systemImage: "arrow.up",
            isDisabled: disabled, isLoading: loading
        ) { onAction(.send) }
    }

    private func save(disabled: Bool, loading: Bool) -> QuickAddFooterItem {
        QuickAddFooterItem(
            id: "primary", title: QuickAddCopy.send, role: .prominent, systemImage: "arrow.up",
            isDisabled: disabled, isLoading: loading
        ) { onAction(.save) }
    }

    private func askingItems(_ draft: ReadingDraft) -> [QuickAddFooterItem] {
        guard let field = draft.nextQuestion else { return [] }
        guard case .typed = QuickAddQuestionKind(field) else {
            return [QuickAddFooterItem(id: "primary", title: QuickAddCopy.editSentence, role: .secondary) {
                onAction(.editSentence)
            }]
        }
        return [confirm(field)]
    }

    private func confirm(_ field: MissingField) -> QuickAddFooterItem {
        let money = QuickAddAnswer.amount(answer)
        let name = QuickAddAnswer.name(answer)
        let usable = field == .amount ? money != nil : name != nil
        return QuickAddFooterItem(id: "primary", title: QuickAddCopy.confirm, role: .prominent, isDisabled: !usable) {
            if field == .amount, let money { onAction(.answerAmount(money)) }
            if field == .description, let name { onAction(.answerName(name)) }
        }
    }
}

struct QuickAddFooterItem: Identifiable {
    let id: String
    let title: String
    let role: GlassActionButton.Role
    var systemImage: String?
    var isDisabled = false
    var isLoading = false
    let action: () -> Void
}

private struct QuickAddFooter: View {
    let items: [QuickAddFooterItem]

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        GlassEffectContainer(spacing: PocketMetrics.footerContainerSpacing) {
            layout {
                ForEach(typeSize.isAccessibilitySize ? Array(items.reversed()) : items) { item in
                    GlassActionButton(
                        title: item.title, role: item.role, systemImage: item.systemImage,
                        isLoading: item.isLoading, action: item.action
                    )
                    .disabled(item.isDisabled)
                }
            }
        }
        .padding(.horizontal, PocketMetrics.screenMargin)
        .padding(.top, PocketMetrics.footerTopPadding)
    }

    private var layout: AnyLayout {
        typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(spacing: PocketMetrics.footerButtonGap))
            : AnyLayout(HStackLayout(spacing: PocketMetrics.footerButtonGap))
    }
}
