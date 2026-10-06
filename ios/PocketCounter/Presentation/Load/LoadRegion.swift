import SwiftUI

/// Renders a `LoadPhase`. Takes the placeholder as a value, not a second view, so a skeleton
/// cannot lay out differently from the real content. A closure, so it is built only for a skeleton.
struct LoadRegion<Value: Equatable, Content: View>: View {
    let phase: LoadPhase<Value>
    let placeholder: () -> Value
    var isRetrying = false
    let onRetry: () -> Void
    @ViewBuilder let content: (Value) -> Content

    /// Time belongs to the view: in a model it would make tests sleep.
    @State private var showsPlaceholder = false

    var body: some View {
        Group {
            switch phase {
            case .firstLoad:
                skeleton
            case .failed(let failure):
                blocking(failure)
            case .loaded(let value):
                content(value)
            case .stale(let value, let failure):
                VStack(alignment: .leading, spacing: 0) {
                    if let notice = LoadFailureMessage.notice(for: failure) {
                        PocketNoticeCard(notice: notice, action: retryAction, isBusy: isRetrying)
                            .padding(.bottom, PocketMetrics.tileSpacing)
                    }

                    content(value)
                }
            }
        }
        .task(id: isFirstLoad) {
            guard isFirstLoad else {
                showsPlaceholder = false
                return
            }
            do { try await Task.sleep(for: PocketMotion.indicatorGrace) } catch { return }
            withAnimation(PocketMotion.quick) { showsPlaceholder = true }
        }
    }

    private var isFirstLoad: Bool {
        phase == .firstLoad
    }

    private var retryAction: PocketInlineMessage.Action {
        .init(title: "Tentar novamente", perform: onRetry)
    }

    private var skeleton: some View {
        ZStack {
            if showsPlaceholder {
                content(placeholder())
                    .redacted(reason: .placeholder)
                    .accessibilityHidden(true)
                    .transition(.opacity)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Carregando")
    }

    @ViewBuilder
    private func blocking(_ failure: LoadFailure) -> some View {
        if let notice = LoadFailureMessage.blocking(for: failure) {
            ContentUnavailableView {
                Label(notice.title, systemImage: notice.kind.symbol)
            } description: {
                if let detail = notice.detail { Text(detail) }
            } actions: {
                if isRetrying {
                    ProgressView()
                } else {
                    Button("Tentar novamente", action: onRetry)
                        .buttonStyle(.bordered)
                }
            }
        }
    }
}
