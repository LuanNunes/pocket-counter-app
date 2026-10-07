import SwiftUI

extension MonthPill {
    init(ledger: MonthLedgerModel) {
        let state = ledger.state
        self.init(
            month: state.month, isCurrent: state.month == .current,
            canGoBack: state.canSelectPrevious, canGoForward: state.canSelectNext,
            onPrevious: { ledger.selectPrevious() }, onNext: { ledger.selectNext() }
        )
    }
}

extension LedgerDegradedNotice {
    init(ledger: MonthLedgerModel, value: MonthLedger) {
        let state = ledger.state
        self.init(
            failed: value.lookups.failed, isStale: state.load.phase.isStale,
            isRetrying: state.load.isLoading, onRetry: { Task { await ledger.refresh() } }
        )
    }
}
