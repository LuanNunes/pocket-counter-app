import SwiftUI

/// The full-width primary action — `.btn.pri` in `glass.css`.
///
/// `.onHero` is a **white fill with a `heroBase` label**: 11.52:1 for the label, and the fill
/// separates from the gradient at 11.52:1 on `heroBase` / 6.64:1 under the highlight. The obvious
/// alternative fails outright — white on the dark-variant `tint` is 2.61:1 and `heroBase` on it
/// 4.42:1, both below AA for a 17pt semibold label.
///
/// Opaque, not `.buttonStyle(.glass)`. Glass belongs on a control floating over *scrolling*
/// content; the auth background is a static gradient, where a translucent capsule reads as a flat
/// slab. That is the "do not force glass" half of the rule.
struct PocketPrimaryButton: View {

    enum Role {
        /// On the hero gradient. The only role the auth stack may use.
        case onHero
        /// In the shell, over `background`/`cell`, where light-variant tint gives 5.25:1.
        case tinted
    }

    private let title: String
    private let role: Role
    private let isLoading: Bool
    private let action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight = PocketMetrics.primaryButtonHeight

    init(_ title: String, role: Role, isLoading: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.role = role
        self.isLoading = isLoading
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            label
        }
        .buttonStyle(PrimaryFillStyle(fill: fill, ink: ink, minHeight: minHeight))
        .disabled(isLoading)
        .accessibilityLabel(title)
        .busy(isLoading)
    }

    /// One `if`/`else` in a single ViewBuilder — two sibling `if`s would render both, which here
    /// would put a spinner next to the title. Width is unchanged either way, so there is no jump.
    @ViewBuilder
    private var label: some View {
        if isLoading {
            ProgressView()
                .tint(ink)
        } else {
            Text(title)
                .pocketFont(PocketFont.valueEmphasis)
                .lineLimit(2)
                .multilineTextAlignment(.center)
        }
    }

    private var fill: Color {
        switch role {
        case .onHero: PocketColor.onHero
        case .tinted: PocketColor.tint
        }
    }

    private var ink: Color {
        switch role {
        case .onHero: PocketColor.heroBase
        case .tinted: PocketColor.onTint
        }
    }
}

/// `glass.css:119-121`: 52pt, radius 26, 40% opacity when disabled. The dimming is read from the
/// environment rather than passed in, so `.disabled()` anywhere up the chain is honoured.
private struct PrimaryFillStyle: ButtonStyle {
    let fill: Color
    let ink: Color
    let minHeight: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        Surface(configuration: configuration, fill: fill, ink: ink, minHeight: minHeight)
    }

    private struct Surface: View {
        let configuration: Configuration
        let fill: Color
        let ink: Color
        let minHeight: CGFloat

        @Environment(\.isEnabled) private var isEnabled

        var body: some View {
            configuration.label
                .foregroundStyle(ink)
                .frame(maxWidth: .infinity, minHeight: minHeight)
                .background(fill, in: .rect(cornerRadius: PocketMetrics.primaryButtonRadius, style: .continuous))
                .opacity(opacity)
        }

        private var opacity: Double {
            guard isEnabled else { return 0.40 }

            return configuration.isPressed ? 0.85 : 1
        }
    }
}

private extension View {
    @ViewBuilder
    func busy(_ isBusy: Bool) -> some View {
        if isBusy {
            accessibilityValue("Carregando")
                .accessibilityAddTraits(.updatesFrequently)
        } else {
            self
        }
    }
}

#if DEBUG
private struct PrimaryButtonPreview<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 16) {
            content
        }
        .padding(PocketMetrics.screenMargin)
        .background { PocketColor.heroSurface.ignoresSafeArea() }
        .preferredColorScheme(.dark)
    }
}

#Preview("On hero") {
    PrimaryButtonPreview {
        PocketPrimaryButton("Entrar", role: .onHero) {}
        PocketPrimaryButton("Criar conta", role: .onHero) {}
    }
}

#Preview("On hero · loading") {
    PrimaryButtonPreview {
        PocketPrimaryButton("Entrar", role: .onHero, isLoading: true) {}
    }
}

#Preview("On hero · disabled") {
    PrimaryButtonPreview {
        PocketPrimaryButton("Entrar", role: .onHero) {}
            .disabled(true)
    }
}

#Preview("On hero · AX5") {
    PrimaryButtonPreview {
        PocketPrimaryButton("Tentar novamente", role: .onHero) {}
    }
    .environment(\.dynamicTypeSize, .accessibility5)
}

#Preview("Tinted") {
    VStack(spacing: 16) {
        PocketPrimaryButton("Salvar", role: .tinted) {}
        PocketPrimaryButton("Salvar", role: .tinted, isLoading: true) {}
        PocketPrimaryButton("Salvar", role: .tinted) {}
            .disabled(true)
    }
    .padding(PocketMetrics.screenMargin)
    .background(PocketColor.background)
}
#endif
