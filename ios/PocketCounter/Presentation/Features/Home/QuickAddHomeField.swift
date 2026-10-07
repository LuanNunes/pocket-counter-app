import SwiftUI

/// Stub: both controls open a placeholder sheet until quick-add exists.
struct QuickAddHomeField: View {
    @State private var isPresenting = false
    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: PocketMetrics.quickAddSpacing) { field; dictation }
            } else {
                HStack(spacing: PocketMetrics.quickAddSpacing) { field; dictation }
            }
        }
        .padding(.horizontal, PocketMetrics.screenMargin)
        .padding(.bottom, PocketMetrics.quickAddBottomPadding)
        .sheet(isPresented: $isPresenting) {
            ContentUnavailableView("Em construção", systemImage: "hammer")
                .presentationDetents([.medium])
        }
    }

    private var field: some View {
        Button { isPresenting = true } label: {
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

    private var dictation: some View {
        Button { isPresenting = true } label: {
            Image(systemName: "mic.fill")
                .pocketFont(PocketFont.body)
                .foregroundStyle(PocketColor.onTint)
                .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                .frame(width: PocketMetrics.quickAddHeight, height: PocketMetrics.quickAddHeight)
                .background(PocketColor.tint, in: .circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Ditar")
    }
}
