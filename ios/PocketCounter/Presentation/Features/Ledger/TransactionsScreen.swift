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
                lookups: value.lookups, state: state,
                reorderNotice: ReorderNotice.message(
                    for: ledger.state.failedReorder, month: ledger.state.month, kind: state.kind),
                writes: ledger.state.writes,
                intents: ledger.state.intents, onAction: perform,
                onToggleStatus: { item in Task { await ledger.togglePaymentStatus(of: item) } },
                onToggleFixo: { item in Task { await ledger.toggleFixo(of: item) } },
                onDelete: { item in Task { await ledger.delete(item) } },
                onRefresh: { Task { await ledger.refresh() } },
                onMove: { ids in
                    Task { await ledger.reorder(ids, of: state.kind, in: ledger.state.month) }
                }
            )
        }
        .environment(\.editMode, .constant(state.isReordering ? .active : .inactive))
        .toolbar {
            if state.isReordering {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(TransactionsCopy.reorderDone) { perform(.endReordering) }
                }
            }
        }
        .searchable(
            text: Binding(get: { state.query }, set: { perform(.setQuery($0)) }),
            prompt: TransactionsCopy.searchPrompt
        )
        .sheet(isPresented: Binding(
            get: { isShowingDetail },
            set: { if !$0 { perform(.closeDetail) } }
        )) { detailSheet }
        .onChange(of: ledger.state.month) { state.monthChanged() }
        .onChange(of: ledger.state.writes) { old, new in announceCompleted(from: old, to: new) }
    }

    private var detailProjection: TransactionDetailProjection? {
        state.detail.map { .of($0, in: ledger.state) }
    }

    private var isShowingDetail: Bool {
        guard case .showing = detailProjection else { return false }
        return true
    }

    @ViewBuilder
    private var detailSheet: some View {
        if case .showing(let detail) = detailProjection {
            TransactionDetailView(
                detail: detail,
                onToggleStatus: { Task { await ledger.togglePaymentStatus(of: detail.item) } },
                onToggleFixo: { Task { await ledger.toggleFixo(of: detail.item) } },
                onDelete: { Task { await ledger.delete(detail.item) } },
                onRefresh: { Task { await ledger.refresh() } },
                onClose: { perform(.closeDetail) }
            )
        }
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
            case .openDetail(let target): state.openDetail(target)
            case .closeDetail: state.closeDetail()
            case .beginReordering: state.beginReordering()
            case .endReordering: state.endReordering()
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
