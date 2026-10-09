import SwiftUI

/// The three correctable rows. At most one is open; choosing a chip applies it and closes the row.
struct QuickAddReviewList: View {
    let rows: [QuickAddReviewRow]
    let chips: (QuickAddReviewRow.Field) -> [QuickAddChip]
    @Binding var opened: QuickAddReviewRow.Field?
    let onChange: (QuickAddChip.Change) -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    static func stripID(_ field: QuickAddReviewRow.Field) -> String { "strip-\(field.rawValue)" }

    var body: some View {
        PocketListSection {
            VStack(spacing: 0) {
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    if index > 0 {
                        PocketRowSeparator()
                    }
                    group(row)
                }
            }
            .clipShape(.rect(cornerRadius: PocketMetrics.listRadius))
        }
        .animation(reduceMotion ? PocketMotion.reduced : PocketMotion.standard, value: opened)
    }

    private func group(_ row: QuickAddReviewRow) -> some View {
        let isOpen = opened == row.field
        return VStack(alignment: .leading, spacing: 0) {
            QuickAddReviewRowButton(row: row, isOpen: isOpen) {
                opened = isOpen ? nil : row.field
            }
            if isOpen {
                strip(row)
                    .id(Self.stripID(row.field))
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            }
        }
        .background(isOpen ? PocketColor.tint.opacity(PocketMetrics.reviewOpenWash) : .clear)
    }

    private func strip(_ row: QuickAddReviewRow) -> some View {
        let options = chips(row.field)
        return VStack(alignment: .leading, spacing: 8) {
            if let unavailable = row.unavailable {
                PocketInlineMessage(kind: .warning, text: unavailable, surface: .onCell)
                    .padding(.leading, -4)
            }
            if options.isEmpty, let note = row.emptyNote {
                Text(verbatim: note)
                    .pocketFont(PocketFont.caption)
                    .foregroundStyle(PocketColor.labelSecondary)
            }
            QuickAddFlowLayout(hSpacing: PocketMetrics.chipGap, vSpacing: 0) {
                ForEach(options) { chip in
                    QuickAddChipButton(chip: chip) {
                        onChange(chip.change)
                        opened = nil
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(PocketMetrics.reviewStripPadding)
    }
}

private struct QuickAddReviewRowButton: View {
    let row: QuickAddReviewRow
    let isOpen: Bool
    let onToggle: () -> Void

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Button(action: onToggle) {
            layout {
                texts
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(verbatim: isOpen ? QuickAddCopy.close : row.action)
                    .pocketFont(PocketFont.link)
                    .foregroundStyle(PocketColor.tint)
                    .fixedSize()
            }
            .padding(.horizontal, PocketMetrics.rowPaddingH)
            .padding(.vertical, PocketMetrics.rowPaddingV)
            .frame(minHeight: PocketMetrics.rowMinHeight)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(row.label)
        .accessibilityValue(spokenValue)
        .accessibilityHint(isOpen ? QuickAddCopy.closeHint : QuickAddCopy.openHint)
        .accessibilityAddTraits(.isButton)
    }

    private var layout: AnyLayout {
        typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 4))
            : AnyLayout(HStackLayout(alignment: .center, spacing: PocketMetrics.rowSpacing))
    }

    private var texts: some View {
        VStack(alignment: .leading, spacing: PocketMetrics.detailTitleSpacing) {
            Text(verbatim: row.label)
                .pocketFont(PocketFont.caption)
                .foregroundStyle(PocketColor.labelSecondary)
            QuickAddFlowLayout(hSpacing: PocketMetrics.valueLineGap, vSpacing: PocketMetrics.valueLineVGap) {
                value
                if let provenance = row.provenance {
                    QuickAddBadge(provenance: provenance)
                }
            }
        }
    }

    private var value: some View {
        HStack(spacing: 6) {
            if let color = row.color {
                LedgerDot(argb: color, size: PocketMetrics.groupDot)
            }
            Text(verbatim: row.value)
                .pocketFont(row.isWeak ? PocketFont.body : PocketFont.fieldValue)
                .italic(row.isWeak)
                .foregroundStyle(row.isWeak ? PocketColor.labelSecondary : PocketColor.label)
        }
    }

    private var spokenValue: String {
        guard let provenance = row.provenance else { return row.spokenValue }
        return "\(row.spokenValue), \(QuickAddCopy.spokenBadge(provenance))"
    }
}
