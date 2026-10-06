import SwiftUI

/// Google's dark variant, per their branding guidelines. Compliance surface: never
/// `.glassEffect` (Google fixes the background), never recolour the mark, never an SF Symbol.
/// Typography is the one relaxed rule — the system font scales, Roboto Medium would not.
/// Must stay below the gradient's top third: the border is 3.62:1 on `heroBase`, 2.09:1 on
/// `heroHighlight`.
struct GoogleSignInButton: View {
    /// Android reads `Continuar com Google`; reconcile against Google's localisation table.
    private static let title = "Continuar com o Google"
    private static let markAsset = "googleGMark"

    var isBusy = false
    let action: () -> Void

    @ScaledMetric(relativeTo: .body) private var minHeight = PocketMetrics.primaryButtonHeight
    @ScaledMetric(relativeTo: .body) private var markSize = PocketMetrics.fieldIconSize

    var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                GoogleMark(asset: Self.markAsset, size: markSize)

                if isBusy {
                    ProgressView().progressViewStyle(.circular).tint(PocketColor.googleButtonInk)
                } else {
                    // Wraps rather than shrinks: below its minimum size is a branding violation.
                    Text(Self.title).pocketFont(PocketFont.valueEmphasis).lineLimit(2)
                }
            }
            .frame(maxWidth: .infinity, minHeight: minHeight)
            .foregroundStyle(PocketColor.googleButtonInk)
            .background {
                RoundedRectangle(cornerRadius: PocketMetrics.primaryButtonRadius, style: .continuous)
                    .fill(PocketColor.googleButtonSurface)
            }
            .overlay {
                // Mandated by Google, and what makes the button visible on this surface at all.
                RoundedRectangle(cornerRadius: PocketMetrics.primaryButtonRadius, style: .continuous)
                    .strokeBorder(PocketColor.googleButtonBorder, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
        .accessibilityLabel(Self.title)
        .accessibilityValue(isBusy ? "Entrando" : "")
    }
}

/// A missing asset renders nothing and logs quietly, which is how a button ships with no logo.
private struct GoogleMark: View {
    let asset: String
    let size: CGFloat

    var body: some View {
        if UIImage(named: asset) == nil {
            #if DEBUG
            let _ = assertionFailure("Missing \"\(asset)\": Google's mark cannot be substituted.")
            #endif
            Color.clear.frame(width: size, height: size)
        } else {
            Image(asset)
                .renderingMode(.original)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
                .accessibilityHidden(true)
        }
    }
}

#if DEBUG
#Preview("Idle") {
    GooglePreview { GoogleSignInButton {} }
}

#Preview("Busy") {
    GooglePreview { GoogleSignInButton(isBusy: true) {} }
}

#Preview("Accessibility size") {
    GooglePreview { GoogleSignInButton {} }
        .environment(\.dynamicTypeSize, .accessibility3)
}

private struct GooglePreview<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            PocketColor.heroBase.ignoresSafeArea()
            content.padding(.horizontal, PocketMetrics.screenMargin)
        }
        .preferredColorScheme(.dark)
    }
}
#endif
