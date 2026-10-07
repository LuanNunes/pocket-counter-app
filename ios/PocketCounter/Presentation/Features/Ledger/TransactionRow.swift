import SwiftUI

/// `.txr`, without its card: the caller applies `pocketCard(_:)`. Reads as one VoiceOver element.
struct TransactionRow: View {
    /// What a row needs while the list is being reordered; absent otherwise.
    struct Reordering {
        let position: String
        let moveUp: (() -> Void)?
        let moveDown: (() -> Void)?
    }

    let content: TransactionRowContent
    var isBusy = false
    var busyLabel = TransactionsCopy.statusSaving
    var notice: PocketNotice? = nil
    var noticeAction: PocketInlineMessage.Action? = nil
    var reordering: Reordering? = nil
    let onToggleStatus: () -> Void
    let onOpen: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showsSpinner = false

    var body: some View {
        VStack(spacing: 0) {
            layout
            if let notice {
                PocketInlineMessage(
                    kind: notice.kind, text: notice.title, secondary: notice.detail, action: noticeAction,
                    surface: .onCell
                )
                .padding(.leading, noticeInset)
                .padding(.trailing, PocketMetrics.txRowPadding.trailing)
                .padding(.bottom, PocketMetrics.rowPaddingV)
            }
        }
        .animation(reduceMotion ? PocketMotion.reduced : PocketMotion.standard, value: notice)
        .sensoryFeedback(.impact(weight: .light), trigger: isBusy) { _, busy in busy }
    }

    private var noticeInset: CGFloat {
        dynamicTypeSize.isAccessibilitySize ? 0 : PocketMetrics.txHairlineInset - PocketMetrics.txRowPadding.leading
    }

    private var layout: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                HStack(alignment: .top, spacing: PocketMetrics.txRowSpacing) {
                    statusPill
                    openButton {
                        VStack(alignment: .leading, spacing: PocketMetrics.rowMetaSpacing) {
                            details
                            trailing(alignment: .leading)
                        }
                        .padding(.vertical, PocketMetrics.rowPaddingV)
                    }
                }
            } else {
                HStack(spacing: PocketMetrics.txRowSpacing) {
                    statusPill
                    openButton {
                        HStack(spacing: PocketMetrics.txRowSpacing) {
                            details
                            Spacer(minLength: 8)
                            trailing(alignment: .trailing)
                        }
                    }
                }
            }
        }
        .padding(PocketMetrics.txRowPadding)
        .frame(minHeight: PocketMetrics.txRowMinHeight)
        .animation(PocketMotion.quick, value: content.isPaid)
        .graced(isActive: isBusy, elapsed: $showsSpinner)
        .accessibilityElement(children: .combine)
        .accessibilityValue([isBusy ? busyLabel : nil, reordering?.position].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(reordering == nil ? .isButton : [])
        .defaultAction(reordering == nil ? onOpen : nil)
        .accessibilityActions {
            if let reordering {
                if let moveUp = reordering.moveUp { Button(TransactionsCopy.moveUp, action: moveUp) }
                if let moveDown = reordering.moveDown { Button(TransactionsCopy.moveDown, action: moveDown) }
            } else {
                if !isBusy {
                    Button(TransactionsCopy.statusToggleLabel(isPaid: content.isPaid), action: onToggleStatus)
                }
            }
        }
    }

    @ViewBuilder
    private func openButton<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        if reordering == nil {
            Button(action: onOpen) {
                content()
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
        } else {
            content()
        }
    }

    private var statusPill: some View {
        Button(action: onToggleStatus) {
            pillFace
        }
        .buttonStyle(.plain)
        .disabled(isBusy || reordering != nil)
        .accessibilityHidden(true)
    }

    private var statusInk: Color { content.isPaid ? PocketColor.incomeInk : PocketColor.warningInk }

    @ViewBuilder
    private var glyph: some View {
        if isBusy && showsSpinner {
            ProgressView().controlSize(.small).tint(statusInk)
        } else {
            Image(systemName: content.isPaid ? "checkmark" : "clock")
                .pocketFont(PocketFont.statusIcon)
                .contentTransition(.symbolEffect(.replace))
        }
    }

    private var pillFace: some View {
        glyph
            .foregroundStyle(statusInk)
            .dynamicTypeSize(...DynamicTypeSize.large)
            .frame(width: PocketMetrics.statusPillSize, height: PocketMetrics.statusPillSize)
            .background(content.isPaid ? PocketColor.incomeSoft : PocketColor.warningSoft, in: .circle)
            .frame(width: PocketMetrics.controlSize, height: PocketMetrics.controlSize)
            .contentShape(.rect)
            .padding(.horizontal, (PocketMetrics.statusPillSize - PocketMetrics.controlSize) / 2)
            .accessibilityHidden(true)
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 4) {
                Text(content.title)
                    .pocketFont(PocketFont.rowTitle)
                    .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 1)

                if content.isFixo {
                    Image(systemName: "pin.fill")
                        .pocketFont(PocketFont.rowMeta)
                        .foregroundStyle(PocketColor.tint)
                        .accessibilityLabel("Fixo")
                }
            }
            meta
        }
    }

    @ViewBuilder
    private var meta: some View {
        if content.tag != nil || content.extraTags > 0 || content.payLabel != nil {
            let line = Group {
                if let tag = content.tag { tagChip(tag) }
                if content.extraTags > 0 {
                    Text(TransactionsCopy.extraTags(content.extraTags)).fontWeight(.semibold)
                }
                if let pay = content.payLabel { Text(pay).lineLimit(1) }
            }
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: PocketMetrics.rowMetaSpacing) { line }
                    .pocketFont(PocketFont.rowMeta)
                    .foregroundStyle(PocketColor.labelSecondary)
            } else {
                HStack(spacing: PocketMetrics.rowMetaSpacing) { line }
                    .pocketFont(PocketFont.rowMeta)
                    .foregroundStyle(PocketColor.labelSecondary)
            }
        }
    }

    private func tagChip(_ tag: TransactionRowContent.TagChip) -> some View {
        HStack(spacing: PocketMetrics.tagChipSpacing) {
            LedgerDot(argb: tag.argb, size: PocketMetrics.tagDot)
            Text(tag.name)
                .pocketFont(PocketFont.tagChip)
                .foregroundStyle(PocketColor.label)
                .lineLimit(1)
        }
        .padding(.horizontal, PocketMetrics.tagChipPaddingH)
        .frame(minHeight: PocketMetrics.tagChipHeight)
        .background(PocketColor.fill, in: .capsule)
    }

    private func trailing(alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            PocketAmount(
                value: content.amount, kind: content.kind == .income ? .income : .expense,
                style: .rowAmount, showsDirection: true
            )
            Text(TransactionsCopy.statusCaption(content.isPaid ? .paid : .pending))
                .textCase(.uppercase)
                .pocketFont(PocketFont.statusCaption)
                .foregroundStyle(content.isPaid ? PocketColor.incomeInk : PocketColor.warningInk)
        }
    }
}

private extension View {
    @ViewBuilder
    func defaultAction(_ action: (() -> Void)?) -> some View {
        if let action {
            accessibilityAction(.default, action)
        } else {
            self
        }
    }
}
