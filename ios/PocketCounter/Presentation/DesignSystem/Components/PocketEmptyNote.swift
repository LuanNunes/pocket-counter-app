import SwiftUI

/// `.empty` — what a committed, empty result says. Never stands in for a failed load.
struct PocketEmptyNote: View {
    let text: String

    var body: some View {
        Text(text)
            .pocketFont(PocketFont.subtitle)
            .foregroundStyle(PocketColor.labelSecondary)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(PocketMetrics.emptyNotePadding)
    }
}

#if DEBUG
#Preview("Empty note") {
    PocketEmptyNote(text: "Nenhuma transação neste mês")
        .background(PocketColor.background)
}
#endif
