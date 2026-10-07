import SwiftUI

/// `MonthScreen` over a `List`, for screens whose rows must stay individually addressable.
/// `header` and `content` emit rows; each carries `pocketListBlock()` or `pocketCard(_:)`.
struct MonthListScreen<Header: View, Content: View>: View {
    let title: String
    let ledger: MonthLedgerModel
    @ViewBuilder var header: () -> Header
    @ViewBuilder var content: (MonthLedger) -> Content

    var body: some View {
        let state = ledger.state
        List {
            MonthPill(ledger: ledger)
                .pocketListBlock()

            header()

            LoadRegionRows(
                phase: state.load.phase, placeholder: { .placeholder(for: state.month) },
                isRetrying: state.load.isLoading, onRetry: { Task { await ledger.refresh() } }
            ) { value in
                let notice = LedgerDegradedNotice(ledger: ledger, value: value)
                if notice.isVisible {
                    notice.pocketListBlock()
                }
                content(value)
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(PocketColor.background)
        .listSectionSeparator(.hidden)
        .environment(\.defaultMinListRowHeight, 0)
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.large)
        .refreshable(action: ledger.refreshAction)
        .scrollDismissesKeyboard(.interactively)
    }
}
