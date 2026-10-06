import SwiftUI

/// The hairline–label–hairline row that separates the two credential paths, matching Android's
/// `AuthScreen.kt`. A gap alone would read as "next step in a sequence"; `ou` says *instead*.
///
/// The hairline is the hero's own separator value, 2.68:1 against `heroBase` — decorative, so
/// below 3:1 is acceptable; the label is 8.89:1. Hidden from VoiceOver: it is a visual device,
/// and the two button labels already say what the choice is.
// Unreferenced until the Google path returns; keep.
struct AuthPathDivider: View {
    private let label: String

    init(_ label: String = "ou") {
        self.label = label
    }

    var body: some View {
        HStack(spacing: 12) {
            line

            Text(label)
                .pocketFont(PocketFont.caption)
                .foregroundStyle(PocketColor.onHero.opacity(0.75))

            line
        }
        .accessibilityHidden(true)
    }

    private var line: some View {
        Rectangle()
            .fill(PocketColor.onHero.opacity(0.16))
            .frame(maxWidth: .infinity)
            .frame(height: PocketMetrics.hairline)
    }
}

#if DEBUG
#Preview("Divider") {
    VStack(spacing: 32) {
        AuthPathDivider()
        AuthPathDivider("ou continue com")
    }
    .padding(PocketMetrics.screenMargin)
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background { PocketColor.heroSurface.ignoresSafeArea() }
    .preferredColorScheme(.dark)
}

#Preview("AX5") {
    AuthPathDivider()
        .padding(PocketMetrics.screenMargin)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background { PocketColor.heroSurface.ignoresSafeArea() }
        .preferredColorScheme(.dark)
        .environment(\.dynamicTypeSize, .accessibility5)
}
#endif
