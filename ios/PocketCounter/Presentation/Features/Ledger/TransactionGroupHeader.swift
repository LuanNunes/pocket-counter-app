import SwiftUI

/// `.txg-h`. An ordinary row, not a `Section` header: `.plain` pins those and this must scroll.
struct TransactionGroupHeader: View {
    let label: LedgerGroupLabel
    let count: Int
    let subtotal: Decimal
    let isGrouped: Bool
    let isCollapsed: Bool
    var isReordering = false
    let onToggle: () -> Void

    var body: some View {
        if isGrouped, !isReordering {
            Button(action: onToggle) { content }
                .buttonStyle(.plain)
                .accessibilityValue(isCollapsed ? "Recolhido" : "Expandido")
        } else {
            content
        }
    }

    private var content: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: PocketMetrics.groupHeaderSpacing) {
                title
                Spacer(minLength: 8)
                subtotalText
            }
            VStack(alignment: .leading, spacing: PocketMetrics.groupHeaderSpacing) {
                title
                subtotalText
            }
        }
        .padding(PocketMetrics.groupHeaderPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(.rect)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }

    private var title: some View {
        HStack(spacing: PocketMetrics.groupHeaderSpacing) {
            if isGrouped {
                Image(systemName: "chevron.down")
                    .pocketFont(PocketFont.groupChevron)
                    .foregroundStyle(PocketColor.labelSecondary)
                    .rotationEffect(.degrees(isCollapsed ? -90 : 0))
                    .opacity(isReordering ? 0 : 1)
                    .accessibilityHidden(true)
                if let argb = label.argb {
                    LedgerDot(argb: argb, size: PocketMetrics.groupDot)
                }
            }

            Text(label.name)
                .pocketFont(PocketFont.groupTitle)
                .foregroundStyle(PocketColor.label)
                .lineLimit(1)

            if isGrouped {
                Text(count, format: .number)
                    .pocketFont(PocketFont.groupCount)
                    .foregroundStyle(PocketColor.labelSecondary)
                    .padding(PocketMetrics.groupCountPadding)
                    .background(PocketColor.fill, in: .rect(cornerRadius: PocketMetrics.groupCountRadius))
            }
        }
    }

    private var subtotalText: some View {
        Text(PocketFormat.currency(subtotal, signed: false))
            .pocketFont(PocketFont.groupSubtotal, tabularFigures: true)
            .foregroundStyle(PocketColor.labelSecondary)
            .lineLimit(1)
    }
}
