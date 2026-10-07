import SwiftUI

/// Nothing to show but the failure: a single block, so it works in a stack and in a `List` row.
struct LoadBlockingView: View {
    let failure: LoadFailure
    let isRetrying: Bool
    let onRetry: () -> Void

    var body: some View {
        if let notice = LoadFailureMessage.blocking(for: failure) {
            ContentUnavailableView {
                Label(notice.title, systemImage: notice.kind.symbol)
            } description: {
                if let detail = notice.detail { Text(detail) }
            } actions: {
                if isRetrying {
                    ProgressView()
                } else {
                    Button("Tentar novamente", action: onRetry)
                        .buttonStyle(.bordered)
                }
            }
        }
    }
}
