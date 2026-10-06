import SwiftUI

/// The round glass button that floats over scrolling content — `.gbtn` in `glass.css`.
///
/// `.buttonStyle(.glass)` already supplies the material and the press response; adding
/// `.glassEffect` on top would stack a second glass layer with a competing interaction.
struct GlassCircleButton: View {
    var systemImage: String
    var accessibilityLabel: String
    var prominent = false
    var action: () -> Void

    @ScaledMetric(relativeTo: .body) private var size: CGFloat = PocketMetrics.controlSize

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .frame(minWidth: size, minHeight: size)
        }
        .buttonStyleForProminence(prominent)
        .accessibilityLabel(accessibilityLabel)
    }
}

private extension View {
    /// `if/else` rather than guard-and-return: a ViewBuilder has no early return, and two
    /// separate `if`s would render both branches.
    @ViewBuilder
    func buttonStyleForProminence(_ prominent: Bool) -> some View {
        if prominent {
            buttonStyle(.glassProminent)
                .tint(PocketColor.tint)
                .foregroundStyle(PocketColor.onTint)
        } else {
            buttonStyle(.glass)
                .foregroundStyle(PocketColor.tint)
        }
    }
}

/// A capsule of controls that reads as one surface — `.gcap`.
///
/// The spec's capsule is itself the glass; its buttons are plain. Giving each child its own
/// glass would render as separate panes rather than one bar.
struct GlassCapsuleBar<Content: View>: View {
    @ViewBuilder var content: Content

    @ScaledMetric(relativeTo: .body) private var height: CGFloat = PocketMetrics.controlSize

    var body: some View {
        HStack(spacing: 0) {
            content
        }
        .frame(height: height)
        .padding(.horizontal, PocketMetrics.capsulePadding)
        .glassEffect(.regular.interactive(), in: .capsule)
    }
}

/// A plain icon button sized for `GlassCapsuleBar`, which carries the glass for the group.
struct GlassBarButton: View {
    var systemImage: String
    var accessibilityLabel: String
    var isEnabled = true
    var action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minWidth: CGFloat = PocketMetrics.capsuleButtonMinWidth

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .frame(minWidth: minWidth, maxHeight: .infinity)
        }
        .buttonStyle(.plain)
        .foregroundStyle(isEnabled ? PocketColor.tint : PocketColor.labelTertiary)
        .disabled(!isEnabled)
        .accessibilityLabel(accessibilityLabel)
    }
}

#Preview("Glass controls") {
    ZStack {
        PocketColor.heroSurface.ignoresSafeArea()

        VStack(spacing: 24) {
            GlassCircleButton(systemImage: "plus", accessibilityLabel: "Adicionar") {}
            GlassCircleButton(systemImage: "plus", accessibilityLabel: "Adicionar", prominent: true) {}

            GlassCapsuleBar {
                GlassBarButton(systemImage: "magnifyingglass", accessibilityLabel: "Buscar") {}
                GlassBarButton(systemImage: "line.3.horizontal.decrease", accessibilityLabel: "Filtrar", isEnabled: false) {}
            }
        }
    }
}
