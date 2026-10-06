import SwiftUI

/// A text field as one row of a field group drawn straight onto the hero gradient.
///
/// Measured on `heroBase`/`heroHighlight`: entered text 11.52/6.64:1, prompt 8.36/4.95:1. The
/// prompt is pinned to 70% white because SwiftUI's own placeholder resolves to ~60%, which is
/// 4.39:1 under the highlight. No fill and no glass behind the field — a translucent well drops
/// white text to 3.71:1, and a `cell`-coloured card is invisible here at 1.48:1.
///
/// Closure-free on purpose: `PocketRow` records that a leading-only initialiser makes every
/// trailing-closure call ambiguous, and a `String?` symbol cannot have that problem. Keyboard,
/// content type and submit label belong to the caller — one row serves `.username`, `.password`,
/// `.newPassword` and `.name`.
///
/// The group container is the caller's, so it stays consistent across screens:
/// ```
/// VStack(spacing: 0) { row; separator; row }
///   .clipShape(.rect(cornerRadius: PocketMetrics.listRadius, style: .continuous))
///   .overlay {
///       RoundedRectangle(cornerRadius: PocketMetrics.listRadius, style: .continuous)
///           .strokeBorder(PocketColor.onHero.opacity(0.22), lineWidth: 1)   // 3.31:1
///   }
/// ```
/// Separator: `Rectangle().fill(PocketColor.onHero.opacity(0.16))` at `PocketMetrics.hairline` —
/// the hero's own KPI hairline, decorative at 2.68:1. On an invalid group the stroke becomes 2pt
/// `PocketColor.onHeroExpense` (5.05:1). Both of those non-white values are measured against
/// `heroBase`, so they hold only below ~55% of screen height, where the gradient's highlight has
/// faded out; above that, only white content is safe.
struct PocketFieldRow: View {

    /// Reinforcement only. The message below the group carries the meaning; hue never does.
    enum Validity {
        case normal
        case invalid
    }

    var systemImage: String?
    var prompt: String
    @Binding var text: String
    var isSecure = false
    var accessibilityLabel: String
    var state: Validity = .normal

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ScaledMetric(relativeTo: .body) private var minHeight = PocketMetrics.fieldRowMinHeight
    @ScaledMetric(relativeTo: .body) private var symbolSize = PocketMetrics.fieldIconSize

    var body: some View {
        HStack(spacing: PocketMetrics.rowSpacing) {
            if let systemImage, !dynamicTypeSize.isAccessibilitySize {
                Image(systemName: systemImage)
                    .font(.system(size: symbolSize))
                    .foregroundStyle(symbolColor)
                    .frame(width: symbolSize)
                    .accessibilityHidden(true)
            }

            field
                .pocketFont(PocketFont.body)
                .foregroundStyle(PocketColor.onHero)
                .tint(PocketColor.tint)
                .accessibilityLabel(accessibilityLabel)
        }
        .padding(.horizontal, PocketMetrics.rowPaddingH)
        .padding(.vertical, PocketMetrics.fieldRowPaddingV)
        .frame(minHeight: minHeight)
    }

    /// One `if`/`else` in a single ViewBuilder: two sibling `if`s would render both fields, which
    /// here means two AutoFill targets for one credential.
    @ViewBuilder
    private var field: some View {
        if isSecure {
            SecureField("", text: $text, prompt: promptText)
        } else {
            TextField("", text: $text, prompt: promptText)
        }
    }

    private var promptText: Text {
        Text(prompt)
            .foregroundStyle(PocketColor.onHero.opacity(0.70))
    }

    /// The group's stroke says *something* is wrong; the symbol says *which row*.
    private var symbolColor: Color {
        switch state {
        case .normal: PocketColor.onHero.opacity(0.70)
        case .invalid: PocketColor.onHeroExpense
        }
    }
}

#if DEBUG

/// The group recipe from the doc comment, so the previews show a row in the only context it has.
private struct FieldGroupPreview<Content: View>: View {
    var isInvalid = false
    @ViewBuilder var content: Content

    var body: some View {
        VStack(spacing: 0) {
            content
        }
        .clipShape(.rect(cornerRadius: PocketMetrics.listRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: PocketMetrics.listRadius, style: .continuous)
                .strokeBorder(stroke, lineWidth: isInvalid ? 2 : 1)
        }
        .padding(PocketMetrics.screenMargin)
        .background { PocketColor.heroSurface.ignoresSafeArea() }
        .preferredColorScheme(.dark)
    }

    private var stroke: Color {
        guard isInvalid else { return PocketColor.onHero.opacity(0.22) }

        return PocketColor.onHeroExpense
    }
}

private struct FieldSeparatorPreview: View {
    var body: some View {
        Rectangle()
            .fill(PocketColor.onHero.opacity(0.16))
            .frame(height: PocketMetrics.hairline)
            .padding(.leading, PocketMetrics.rowPaddingH)
    }
}

#Preview("Empty") {
    @Previewable @State var email = ""
    @Previewable @State var password = ""

    FieldGroupPreview {
        PocketFieldRow(systemImage: "envelope", prompt: "E-mail", text: $email, accessibilityLabel: "E-mail")
        FieldSeparatorPreview()
        PocketFieldRow(systemImage: "lock", prompt: "Senha", text: $password, isSecure: true,
                       accessibilityLabel: "Senha")
    }
}

#Preview("Filled") {
    @Previewable @State var email = "ana@pocket-counter.com"
    @Previewable @State var password = "senhaforte"

    FieldGroupPreview {
        PocketFieldRow(systemImage: "envelope", prompt: "E-mail", text: $email, accessibilityLabel: "E-mail")
        FieldSeparatorPreview()
        PocketFieldRow(systemImage: "lock", prompt: "Senha", text: $password, isSecure: true,
                       accessibilityLabel: "Senha")
    }
}

#Preview("Invalid") {
    @Previewable @State var email = "ana@pocket-counter"
    @Previewable @State var password = "1234"

    FieldGroupPreview(isInvalid: true) {
        PocketFieldRow(systemImage: "envelope", prompt: "E-mail", text: $email,
                       accessibilityLabel: "E-mail", state: .invalid)
        FieldSeparatorPreview()
        PocketFieldRow(systemImage: "lock", prompt: "Senha", text: $password, isSecure: true,
                       accessibilityLabel: "Senha", state: .invalid)
    }
}

#Preview("No symbol · AX5") {
    @Previewable @State var name = "Ana"

    FieldGroupPreview {
        PocketFieldRow(systemImage: nil, prompt: "Nome", text: $name, accessibilityLabel: "Nome")
    }
    .environment(\.dynamicTypeSize, .accessibility5)
}
#endif
