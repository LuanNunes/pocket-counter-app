import SwiftUI

/// Where a transaction row sits in its group, which decides the corners it rounds.
enum PocketCardPosition: Equatable {
    case only, first, middle, last

    static func of(index: Int, count: Int) -> PocketCardPosition {
        guard count > 1 else { return .only }
        guard index > 0 else { return .first }
        return index == count - 1 ? .last : .middle
    }

    fileprivate var roundsTop: Bool { self == .only || self == .first }
    fileprivate var roundsBottom: Bool { self == .only || self == .last }
    fileprivate var hasHairline: Bool { !roundsTop }
}

extension View {
    /// A whole block — a `PocketListSection`, the pill, a notice — as one full-width, chromeless
    /// `List` row.
    func pocketListBlock() -> some View {
        listBlockLayout().listRowBackground(Color.clear)
    }

    /// One transaction as a slice of a 26pt card. The card is the row *background*, never
    /// `.background` on the content: only then does the reorder grip land inside it and the card
    /// keep its width in edit mode. The hairline is part of that background, never a separator row.
    func pocketCard(_ position: PocketCardPosition) -> some View {
        padding(.horizontal, PocketMetrics.screenMargin)
            .listBlockLayout()
            .listRowBackground(PocketCardBackground(position: position))
    }

    private func listBlockLayout() -> some View {
        listRowInsets(EdgeInsets()).listRowSeparator(.hidden)
    }
}

private struct PocketCardBackground: View {
    let position: PocketCardPosition

    var body: some View {
        UnevenRoundedRectangle(
            topLeadingRadius: position.roundsTop ? PocketMetrics.listRadius : 0,
            bottomLeadingRadius: position.roundsBottom ? PocketMetrics.listRadius : 0,
            bottomTrailingRadius: position.roundsBottom ? PocketMetrics.listRadius : 0,
            topTrailingRadius: position.roundsTop ? PocketMetrics.listRadius : 0
        )
        .fill(PocketColor.cell)
        .overlay(alignment: .top) {
            if position.hasHairline {
                PocketRowSeparator(inset: PocketMetrics.txHairlineInset)
            }
        }
        .padding(.horizontal, PocketMetrics.screenMargin)
    }
}
