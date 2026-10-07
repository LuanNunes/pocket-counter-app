import SwiftUI

/// The degraded-lookup card. Absent while stale: one notice at a time, and stale outranks degraded.
struct LedgerDegradedNotice: View {
    let failed: Set<LookupKind>
    let isStale: Bool
    let isRetrying: Bool
    let onRetry: () -> Void

    var isVisible: Bool { notice != nil }

    private var notice: PocketNotice? {
        isStale ? nil : LoadFailureMessage.degraded(failed)
    }

    var body: some View {
        if let notice {
            LoadNoticeCard(notice: notice, isRetrying: isRetrying, onRetry: onRetry)
        }
    }
}
