import SwiftUI

/// The Transações tab: owns the view state and hands everything else to `TransactionsView`.
struct TransactionsScreen: View {
    let ledger: MonthLedgerModel

    @State private var state = TransactionsViewState()

    var body: some View {
        MonthListScreen(title: "Transações", ledger: ledger) {
            KindPicker(kind: state.kind, onSelect: { perform(.selectKind($0)) })
                .pocketListBlock()
        } content: { value in
            TransactionsView(
                board: .from(value, filter: state.filter, mode: state.mode),
                lookups: value.lookups, state: state, writes: ledger.state.writes, onAction: perform,
                onToggleStatus: { item in Task { await ledger.togglePaymentStatus(of: item) } },
                onRefresh: { Task { await ledger.refresh() } }
            )
        }
        .searchable(
            text: Binding(get: { state.query }, set: { perform(.setQuery($0)) }),
            prompt: TransactionsCopy.searchPrompt
        )
        .onChange(of: ledger.state.month) { state.monthChanged() }
        .onChange(of: ledger.state.writes) { old, new in announceCompleted(from: old, to: new) }
    }

    private func announceCompleted(from old: [TransactionID: PaymentStatusWrite], to new: [TransactionID: PaymentStatusWrite]) {
        let items = ledger.state.load.value?.items ?? []
        let statuses = Dictionary(items.map { ($0.id, $0.statusPayment) }, uniquingKeysWith: { first, _ in first })
        TransactionRowWrite.completed(from: old, to: new, statusOf: { statuses[$0] }).forEach {
            AccessibilityNotification.Announcement(TransactionsCopy.statusMarked($0)).post()
        }
    }

    private func perform(_ action: TransactionsAction) {
        withAnimation(PocketMotion.quick) {
            switch action {
            case .selectKind(let kind): state.select(kind: kind)
            case .selectMode(let mode): state.select(mode: mode)
            case .setQuery(let query): state.set(query: query)
            case .toggleOnlyFixos: state.toggleOnlyFixos()
            case .toggleGroup(let identity): state.toggle(identity)
            }
        }
    }
}

private struct KindPicker: View {
    let kind: TransactionType
    let onSelect: @MainActor (TransactionType) -> Void

    var body: some View {
        Picker("Tipo", selection: Binding(get: { kind }, set: onSelect)) {
            ForEach([TransactionType.expense, .income], id: \.self) { kind in
                Text(TransactionsCopy.kindName(kind)).tag(kind)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal, PocketMetrics.screenMargin)
    }
}
