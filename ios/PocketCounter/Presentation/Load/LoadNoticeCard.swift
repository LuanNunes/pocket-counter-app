import SwiftUI

/// The one rendering of a notice above loaded content, with the retry every load notice offers.
struct LoadNoticeCard: View {
    let notice: PocketNotice
    let isRetrying: Bool
    let onRetry: () -> Void

    var body: some View {
        PocketNoticeCard(
            notice: notice, action: .init(title: "Tentar novamente", perform: onRetry), isBusy: isRetrying
        )
        .padding(.bottom, PocketMetrics.tileSpacing)
    }
}
