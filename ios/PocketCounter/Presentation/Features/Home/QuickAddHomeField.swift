import SwiftUI

struct QuickAddHomeField: View {
    let onOpen: () -> Void

    var body: some View {
        field
            .padding(.horizontal, PocketMetrics.screenMargin)
            .padding(.bottom, PocketMetrics.quickAddBottomPadding)
    }

    private var field: some View {
        Button(action: onOpen) {
            HStack(spacing: PocketMetrics.quickAddIconSpacing) {
                Image(systemName: "sparkles")
                    .pocketFont(PocketFont.controlLabel)
                    .foregroundStyle(PocketColor.tint)
                    .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                    .frame(width: PocketMetrics.quickAddIconSize, height: PocketMetrics.quickAddIconSize)
                    .background(PocketColor.tintSoft, in: .circle)
                    .accessibilityHidden(true)

                Text("O que você gastou ou recebeu?")
                    .pocketFont(PocketFont.controlLabel)
                    .foregroundStyle(PocketColor.labelSecondary)
                    .multilineTextAlignment(.leading)

                Spacer(minLength: 0)
            }
            .padding(PocketMetrics.quickAddInnerPadding)
            .frame(minHeight: PocketMetrics.quickAddHeight)
            .background(PocketColor.cell, in: .capsule)
            .overlay {
                Capsule().strokeBorder(PocketColor.tint.opacity(0.22), lineWidth: PocketMetrics.quickAddRing)
            }
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
    }
}
