import SwiftUI

/// `LoadRegion` for a `List`: emits sibling rows instead of one stacked block, so every
/// transaction stays its own row. `content` must emit rows.
struct LoadRegionRows<Value: Equatable, Content: View>: View {
    let phase: LoadPhase<Value>
    let placeholder: () -> Value
    var isRetrying = false
    let onRetry: () -> Void
    @ViewBuilder let content: (Value) -> Content

    @State private var showsPlaceholder = false

    var body: some View {
        Group {
            switch LoadPlan(phase) {
            case .skeleton:
                skeleton
            case .blocking(let failure):
                LoadBlockingView(failure: failure, isRetrying: isRetrying, onRetry: onRetry)
                    .frame(maxWidth: .infinity)
                    .pocketListBlock()
            case .content(let value, let notice):
                if let notice {
                    LoadNoticeCard(notice: notice, isRetrying: isRetrying, onRetry: onRetry)
                        .pocketListBlock()
                }
                content(value)
            }
        }
        .graced(isActive: phase == .firstLoad, elapsed: $showsPlaceholder)
    }

    @ViewBuilder
    private var skeleton: some View {
        Color.clear
            .frame(height: 1)
            .pocketListBlock()
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Carregando")

        if showsPlaceholder {
            content(placeholder())
                .redacted(reason: .placeholder)
                .accessibilityHidden(true)
                .allowsHitTesting(false)
        }
    }
}
