import SwiftUI

/// The month switcher — `.mpill`. A solid `cell` capsule in the scroll content, never glass: over
/// a static grouped background glass reads as a flat slab.
struct MonthPill: View {
    let month: RefYearMonth
    let isCurrent: Bool
    let canGoBack: Bool
    let canGoForward: Bool
    let onPrevious: () -> Void
    let onNext: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            chevron("chevron.left", isEnabled: canGoBack, action: onPrevious)

            Spacer(minLength: 0)

            HStack(spacing: PocketMetrics.monthPillLabelSpacing) {
                Text(PocketFormat.monthName(month, capitalized: true))
                    .pocketFont(PocketFont.controlLabelEmphasis)
                    .foregroundStyle(PocketColor.label)

                Text(String(month.year))
                    .pocketFont(PocketFont.controlLabel)
                    .foregroundStyle(PocketColor.labelSecondary)

                if isCurrent {
                    Text("atual")
                        .pocketFont(PocketFont.eyebrow)
                        .textCase(.uppercase)
                        .foregroundStyle(PocketColor.tintInk)
                        .padding(PocketMetrics.badgePadding)
                        .background(PocketColor.tintSoft, in: .rect(cornerRadius: PocketMetrics.badgeRadius))
                }
            }

            Spacer(minLength: 0)

            chevron("chevron.right", isEnabled: canGoForward, action: onNext)
        }
        .padding(.horizontal, PocketMetrics.monthPillInnerPadding)
        .frame(minHeight: PocketMetrics.monthPillHeight)
        .background(PocketColor.cell, in: .capsule)
        .padding(.horizontal, PocketMetrics.screenMargin)
        .padding(.top, PocketMetrics.monthPillTopPadding)
        .padding(.bottom, PocketMetrics.monthPillBottomPadding)
        .sensoryFeedback(.selection, trigger: month)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Mês")
        .accessibilityValue(accessibilityValue)
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: if canGoForward { onNext() }
            case .decrement: if canGoBack { onPrevious() }
            @unknown default: break
            }
        }
    }

    private var accessibilityValue: String {
        let label = PocketFormat.monthLabel(year: month.year, month: month.month, showingYear: true)
        return isCurrent ? "\(label), atual" : label
    }

    private func chevron(_ symbol: String, isEnabled: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .frame(width: PocketMetrics.monthPillButton, height: PocketMetrics.monthPillButton)
                .frame(minWidth: PocketMetrics.controlSize, minHeight: PocketMetrics.controlSize)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isEnabled ? PocketColor.tint : PocketColor.labelTertiary)
        .disabled(!isEnabled)
    }
}

#if DEBUG
#Preview("Month pill") {
    VStack(spacing: 0) {
        MonthPill(month: .current, isCurrent: true, canGoBack: true, canGoForward: true,
                  onPrevious: {}, onNext: {})
        MonthPill(month: .current.previous(), isCurrent: false, canGoBack: false, canGoForward: true,
                  onPrevious: {}, onNext: {})
    }
    .frame(maxHeight: .infinity, alignment: .top)
    .background(PocketColor.background)
}
#endif
