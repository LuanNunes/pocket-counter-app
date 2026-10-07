import SwiftUI

/// Renders a `LoadPhase`. Takes the placeholder as a value, not a second view, so a skeleton
/// cannot lay out differently from the real content. A closure, so it is built only for a skeleton.
struct LoadRegion<Value: Equatable, Content: View>: View {
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
            case .content(let value, let notice):
                loaded(value, notice: notice)
            }
        }
        .graced(isActive: isFirstLoad, elapsed: $showsPlaceholder)
    }

    private var isFirstLoad: Bool {
        phase == .firstLoad
    }

    private var skeleton: some View {
        ZStack {
            if showsPlaceholder {
                content(placeholder())
                    .redacted(reason: .placeholder)
                    .accessibilityHidden(true)
                    .allowsHitTesting(false)
                    .transition(.opacity)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Carregando")
    }

    @ViewBuilder
    private func loaded(_ value: Value, notice: PocketNotice?) -> some View {
        if let notice {
            VStack(alignment: .leading, spacing: 0) {
                LoadNoticeCard(notice: notice, isRetrying: isRetrying, onRetry: onRetry)

                content(value)
            }
        } else {
            content(value)
        }
    }
}
