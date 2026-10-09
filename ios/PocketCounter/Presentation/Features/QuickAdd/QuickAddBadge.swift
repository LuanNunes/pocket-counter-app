import SwiftUI

/// "da frase" / "assumido" / "definido". Read aloud as part of its row's value, never on its own.
struct QuickAddBadge: View {
    let provenance: FieldProvenance

    var body: some View {
        Text(verbatim: QuickAddCopy.badge(provenance))
            .pocketFont(PocketFont.eyebrow)
            .textCase(.uppercase)
            .foregroundStyle(ink)
            .padding(PocketMetrics.badgePadding)
            .background(fill, in: .capsule)
            .lineLimit(1)
            .fixedSize()
            .accessibilityHidden(true)
    }

    private var ink: Color {
        switch provenance {
        case .fromSentence: PocketColor.incomeInk
        case .assumed: PocketColor.warningInk
        case .defined: PocketColor.tintInk
        }
    }

    private var fill: Color {
        switch provenance {
        case .fromSentence: PocketColor.incomeSoft
        case .assumed: PocketColor.warningSoft
        case .defined: PocketColor.tintSoft
        }
    }
}
