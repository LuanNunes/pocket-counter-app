import SwiftUI

/// The identity of what is being, or was, written: amount, name and kind.
struct QuickAddIdentity: View {
    let entry: TransactionEntry
    var showsOrb = false
    let status: String
    let spokenStatus: String
    var takesFocus = true

    @AccessibilityFocusState private var isFocused: Bool

    var body: some View {
        VStack(spacing: PocketMetrics.detailTitleSpacing) {
            if showsOrb {
                QuickAddOrb()
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .padding(.bottom, PocketMetrics.receiptAmountGap - PocketMetrics.detailTitleSpacing)
            }
            PocketAmount(
                value: entry.amount.amount, kind: entry.type == .income ? .income : .expense,
                style: .detailAmount, showsDirection: true
            )
            .lineLimit(1)
            .minimumScaleFactor(0.5)
            Text(verbatim: entry.name)
                .pocketFont(PocketFont.valueEmphasis)
                .lineLimit(3)
                .multilineTextAlignment(.center)
            Text(verbatim: status)
                .pocketFont(PocketFont.caption)
                .foregroundStyle(PocketColor.labelSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(PocketMetrics.receiptPadding)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(spokenStatus)
        .accessibilityValue(
            "\(PocketFormat.spokenMovement(entry.amount.amount, isIncome: entry.type == .income)), \(entry.name)"
        )
        .accessibilityAddTraits(.isHeader)
        .accessibilityFocused($isFocused)
        .task { isFocused = takesFocus }
    }
}

/// The `saved` stage, centred in the free height: nothing is editable after the write.
struct QuickAddReceipt: View {
    let entry: TransactionEntry

    var body: some View {
        QuickAddIdentity(
            entry: entry, showsOrb: true, status: QuickAddCopy.receiptStatus(entry.type),
            spokenStatus: QuickAddCopy.spokenReceiptStatus(entry.type)
        )
            .containerRelativeFrame(.vertical, alignment: .center)
    }
}

private struct QuickAddOrb: View {
    @ScaledMetric(relativeTo: .largeTitle) private var size = PocketMetrics.receiptOrb

    var body: some View {
        Image(systemName: "checkmark")
            .pocketFont(PocketFont.notice)
            .foregroundStyle(PocketColor.incomeInk)
            .frame(width: size, height: size)
            .background(PocketColor.incomeSoft, in: .circle)
            .accessibilityHidden(true)
    }
}
