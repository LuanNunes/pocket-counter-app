import SwiftUI

struct HomeScreen: View {
    let ledger: MonthLedgerModel
    let onSelectTab: SelectTabAction
    let quickAdd: @MainActor () -> QuickAddModel

    @State private var sheet: QuickAddModel?
    @State private var dismissed: QuickAddModel?

    var body: some View {
        MonthScreen(title: "Início", ledger: ledger) { value in
            HomeView(summary: .from(value), onAction: perform, onQuickAdd: open)
        }
        .sheet(item: $sheet, onDismiss: reload) { model in
            QuickAddSheet(model: model, onFinish: { sheet = nil })
        }
    }

    private func open() {
        sheet = quickAdd()
        dismissed = sheet
    }

    // Runs on every way out: the receipt can be swiped away and the row is written regardless.
    private func reload() {
        defer { dismissed = nil }
        guard dismissed?.state.didWrite == true else { return }
        Task { await ledger.refresh() }
    }

    private func perform(_ action: HomeAction) {
        switch action {
        case .showCards: onSelectTab(.cartoes)
        case .showTransactions: onSelectTab(.transacoes)
        }
    }
}
