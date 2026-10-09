import SwiftUI

/// The footer's full-width glass action — `.btn.pri` and `.btn.sec` in `glass.css`.
///
/// `PocketPrimaryButton` is deliberately opaque; this one is for a control floating over
/// scrolling content. Group siblings in a `GlassEffectContainer`.
struct GlassActionButton: View {
    enum Role {
        case prominent
        case secondary
    }

    let title: String
    var role: Role = .prominent
    var systemImage: String?
    var isLoading = false
    let action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight = PocketMetrics.primaryButtonHeight

    var body: some View {
        Button(action: action) {
            label
                .frame(maxWidth: .infinity, minHeight: minHeight)
        }
        .glassActionStyle(role)
        .buttonBorderShape(.capsule)
        .disabled(isLoading)
        .accessibilityLabel(title)
        .accessibilityValue(isLoading ? "Carregando" : "")
    }

    @ViewBuilder
    private var label: some View {
        if isLoading {
            ProgressView()
        }
        if !isLoading {
            HStack(spacing: PocketMetrics.rowSpacing) {
                if let systemImage {
                    Image(systemName: systemImage)
                }
                Text(verbatim: title)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
            }
            .pocketFont(PocketFont.valueEmphasis)
        }
    }
}

private extension View {
    @ViewBuilder
    func glassActionStyle(_ role: GlassActionButton.Role) -> some View {
        switch role {
        case .prominent:
            buttonStyle(.glassProminent)
                .tint(PocketColor.tint)
                .foregroundStyle(PocketColor.onTint)
        case .secondary:
            buttonStyle(.glass)
                .foregroundStyle(PocketColor.tint)
        }
    }
}
