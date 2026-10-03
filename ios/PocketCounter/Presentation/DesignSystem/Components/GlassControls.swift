import SwiftUI

/// The round glass button that floats over scrolling content — `.gbtn` in `glass.css`.
///
/// The prototype hand-builds the material because CSS has no Liquid Glass. Here the system
/// provides it, including the interactive press response, so there is no blur, border or
/// inner highlight to reproduce.
struct GlassCircleButton: View {
    var systemImage: String
    var label: String
    var prominent = false
    var action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .frame(width: PocketMetrics.controlSize, height: PocketMetrics.controlSize)
        }
        .buttonStyle(.glass)
        .glassEffect(
            prominent ? .regular.tint(PocketColor.tint).interactive() : .regular.interactive(),
            in: .circle
        )
        .foregroundStyle(prominent ? PocketColor.onHero : PocketColor.tint)
        .accessibilityLabel(label)
    }
}

/// A capsule of glass controls that should read as one group — `.gcap`.
///
/// The container is what makes neighbouring glass merge and morph together instead of
/// looking like two stacked panes.
struct GlassCapsuleBar<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        GlassEffectContainer {
            HStack(spacing: 2) {
                content
            }
            .frame(height: PocketMetrics.controlSize)
        }
    }
}

#Preview("Glass controls") {
    ZStack {
        PocketColor.heroSurface.ignoresSafeArea()

        VStack(spacing: 24) {
            GlassCircleButton(systemImage: "plus", label: "Adicionar") {}
            GlassCircleButton(systemImage: "plus", label: "Adicionar", prominent: true) {}

            GlassCapsuleBar {
                GlassCircleButton(systemImage: "chevron.left", label: "Mês anterior") {}
                GlassCircleButton(systemImage: "chevron.right", label: "Próximo mês") {}
            }
        }
    }
}
