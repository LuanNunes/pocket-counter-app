import SwiftUI

struct HomeScreen: View {
    let ledger: MonthLedgerModel
    let onSelectTab: SelectTabAction

    var body: some View {
        MonthScreen(title: "Início", ledger: ledger) { value in
            HomeView(summary: .from(value), onAction: perform)
        }
    }

    private func perform(_ action: HomeAction) {
        switch action {
        case .showCards: onSelectTab(.cartoes)
        case .showTransactions: onSelectTab(.transacoes)
        }
    }
}
