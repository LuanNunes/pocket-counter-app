import Foundation

@testable import PocketCounter

@MainActor
enum LedgerModelFixture {
    /// The verbs a test does not exercise default to no-ops. The production `init` has no defaults.
    static func model(
        window: MonthWindow = .around(.current),
        month: RefYearMonth = .current,
        loadMonth: @escaping LoadMonthAction,
        setPaymentStatus: @escaping SetPaymentStatusAction = { _, _ in },
        toggleFixo: @escaping ToggleFixoAction = { _ in },
        deleteTransaction: @escaping DeleteTransactionAction = { _ in },
        onSessionExpired: @escaping SessionExpiredAction
    ) -> MonthLedgerModel {
        MonthLedgerModel(
            window: window, month: month, loadMonth: loadMonth, setPaymentStatus: setPaymentStatus,
            toggleFixo: toggleFixo, deleteTransaction: deleteTransaction, onSessionExpired: onSessionExpired)
    }
}
