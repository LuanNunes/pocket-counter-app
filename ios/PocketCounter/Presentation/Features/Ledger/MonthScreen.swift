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
            MonthPill(
                month: state.month, isCurrent: state.month == .current,
                canGoBack: state.canSelectPrevious, canGoForward: state.canSelectNext,
                onPrevious: { ledger.selectPrevious() }, onNext: { ledger.selectNext() }
            )
            LoadRegion(
                phase: state.load.phase, placeholder: { .placeholder(for: state.month) },
                isRetrying: state.load.isLoading, onRetry: { Task { await ledger.refresh() } }
            ) { value in
                VStack(alignment: .leading, spacing: 0) {
                    degradedNotice(value, isRetrying: state.load.isLoading, isStale: isStale(state.load.phase))
                    content(value)
                }
            }
        }
    }

    private func isStale(_ phase: LoadPhase<MonthLedger>) -> Bool {
        guard case .stale = phase else { return false }
        return true
    }

    @ViewBuilder
    private func degradedNotice(_ value: MonthLedger, isRetrying: Bool, isStale: Bool) -> some View {
        if !isStale, let notice = LoadFailureMessage.degraded(value.lookups.failed) {
            PocketNoticeCard(
                notice: notice,
                action: .init(title: "Tentar novamente") { Task { await ledger.refresh() } },
                isBusy: isRetrying
            )
            .padding(.bottom, PocketMetrics.tileSpacing)
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
