import SwiftUI

/// A screen over the shared month: pill, load region and the degraded-lookup notice.
/// The shell drives loading; this never does.
struct MonthScreen<Content: View, Bar: ToolbarContent>: View {
    let title: String
    let ledger: MonthLedgerModel
    @ToolbarContentBuilder var toolbar: () -> Bar
    @ViewBuilder var content: (MonthLedger) -> Content

    var body: some View {
        let state = ledger.state
        Screen(title: title, onRefresh: ledger.refreshAction, toolbar: toolbar) {
            MonthPill(ledger: ledger)
            LoadRegion(
                phase: state.load.phase, placeholder: { .placeholder(for: state.month) },
                isRetrying: state.load.isLoading, onRetry: { Task { await ledger.refresh() } }
            ) { value in
                VStack(alignment: .leading, spacing: 0) {
                    LedgerDegradedNotice(ledger: ledger, value: value)
                    content(value)
                }
            }
        }
    }
}

extension MonthScreen where Bar == ToolbarItemGroup<EmptyView> {
    init(title: String, ledger: MonthLedgerModel, @ViewBuilder content: @escaping (MonthLedger) -> Content) {
        self.init(
            title: title, ledger: ledger,
            toolbar: { return ToolbarItemGroup { EmptyView() } }, content: content
        )
    }
}
