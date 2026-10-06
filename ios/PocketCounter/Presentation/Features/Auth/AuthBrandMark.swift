import SwiftUI

/// The wordmark. Typographic because there is no logo asset: the only artwork in the catalog is
/// inside `AppIcon.appiconset`, which is not addressable as an `Image`. When a real mark lands it
/// drops into this one component.
///
/// Both sizes are white on the gradient — 8.89:1 at 75% for the eyebrow, 11.52:1 solid for the
/// hero (6.64:1 under the highlight, which is where both of them sit). Recorded for whoever
/// replaces this with artwork: the prototype's `.av` monogram gradient cannot carry a white
/// letter, which measures 2.75:1 at its light end; a white tile with a `heroBase` letter is the
/// contrast-safe version of the same idea.
struct AuthBrandMark: View {

    enum Size {
        /// The eyebrow above a screen title.
        case compact
        /// Centred, for the launch splash.
        case hero
    }

    private static let wordmark = "PocketCounter"

    private let size: Size

    init(_ size: Size) {
        self.size = size
    }

    var body: some View {
        mark
            .accessibilityLabel(Self.wordmark)
    }

    /// One `if`/`else`: two sibling `if`s in a ViewBuilder would draw both sizes.
    @ViewBuilder
    private var mark: some View {
        if size == .hero {
            Text(Self.wordmark)
                .pocketFont(PocketFont.largeTitle)
                .foregroundStyle(PocketColor.onHero)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
        } else {
            Text(Self.wordmark)
                .textCase(.uppercase)
                .pocketFont(PocketFont.eyebrow)
                .foregroundStyle(PocketColor.onHero.opacity(0.75))
        }
    }
}

#if DEBUG
#Preview("Both sizes") {
    VStack(alignment: .leading, spacing: 32) {
        AuthBrandMark(.compact)
        AuthBrandMark(.hero)
    }
    .padding(PocketMetrics.screenMargin)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background { PocketColor.heroSurface.ignoresSafeArea() }
    .preferredColorScheme(.dark)
}

#Preview("AX5") {
    VStack(alignment: .leading, spacing: 32) {
        AuthBrandMark(.compact)
        AuthBrandMark(.hero)
    }
    .padding(PocketMetrics.screenMargin)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background { PocketColor.heroSurface.ignoresSafeArea() }
    .preferredColorScheme(.dark)
    .environment(\.dynamicTypeSize, .accessibility5)
}
#endif
