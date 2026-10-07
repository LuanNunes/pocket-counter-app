import SwiftUI

/// `.txsum`: the month's total for the kind on screen, and the grouping chip.
struct TransactionsSummary: View {
    let total: Decimal
    let count: Int
    let kind: TransactionType
    let mode: LedgerGroupMode
    let onlyFixos: Bool
    let onAction: (TransactionsAction) -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: PocketMetrics.rowMetaSpacing))
            : AnyLayout(HStackLayout(alignment: .bottom))
        layout {
            VStack(alignment: .leading, spacing: 0) {
                PocketAmount(
                    value: total, kind: kind == .income ? .income : .expense, style: .summaryValue, signed: false)
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                Text(TransactionsCopy.count(count, kind: kind))
                    .pocketFont(PocketFont.caption)
                    .foregroundStyle(PocketColor.labelSecondary)
            }
            .accessibilityElement(children: .combine)

            Spacer(minLength: 8)

            modeChip
        }
        .padding(PocketMetrics.summaryPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var modeChip: some View {
        Menu {
            Picker(TransactionsCopy.groupingLabel, selection: Binding(get: { mode }, set: { onAction(.selectMode($0)) })) {
                ForEach(LedgerGroupMode.allCases, id: \.self) { mode in
                    Text(TransactionsCopy.modeName(mode)).tag(mode)
                }
            }
            Toggle(TransactionsCopy.onlyFixos, isOn: Binding(get: { onlyFixos }, set: { _ in onAction(.toggleOnlyFixos) }))
        } label: {
            HStack(spacing: PocketMetrics.modeChipSpacing) {
                Text(TransactionsCopy.modeName(mode))
                Image(systemName: "chevron.up.chevron.down")
                    .accessibilityHidden(true)
            }
            .pocketFont(PocketFont.link)
            .padding(.horizontal, PocketMetrics.modeChipPaddingH)
            .frame(minHeight: PocketMetrics.modeChipHeight)
            .background(PocketColor.fill, in: .capsule)
        }
        .fixedSize()
        .accessibilityLabel(TransactionsCopy.groupingLabel)
        .accessibilityValue(TransactionsCopy.modeName(mode))
    }
}
