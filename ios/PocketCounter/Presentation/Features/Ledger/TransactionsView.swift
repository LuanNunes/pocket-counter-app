import SwiftUI

/// The rows under the segmented control: summary, then groups and the footer, or the one empty
/// state the board names. Emits `List` rows; takes no model.
struct TransactionsView: View {
    let board: LedgerBoard
    let lookups: LookupSet
    let state: TransactionsViewState
    let writes: [TransactionID: PaymentStatusWrite]
    let onAction: (TransactionsAction) -> Void
    let onToggleStatus: (HistoryItem) -> Void
    let onRefresh: () -> Void

    var body: some View {
        TransactionsSummary(
            total: board.total.amount, count: board.visibleCount, kind: state.kind, mode: state.mode,
            onlyFixos: state.onlyFixos, onAction: onAction
        )
        .pocketListBlock()

        switch board.emptiness {
        case .notEmpty:
            groups
            footer
        case .monthHasNoneOfKind:
            PocketEmptyNote(text: TransactionsCopy.emptyMonth(state.kind))
                .pocketListBlock()
        case .filteredOut:
            ContentUnavailableView(
                TransactionsCopy.filteredOutTitle, systemImage: "magnifyingglass",
                description: Text(TransactionsCopy.filteredOutDetail)
            )
            .pocketListBlock()
        }
    }

    @ViewBuilder
    private var groups: some View {
        ForEach(board.groups) { group in
            let isCollapsed = state.collapsed.contains(group.identity)
            TransactionGroupHeader(
                label: .of(group.identity, lookups: lookups), count: group.items.count,
                subtotal: group.subtotal.amount, isGrouped: state.mode != .lista, isCollapsed: isCollapsed,
                onToggle: { onAction(.toggleGroup(group.identity)) }
            )
            .pocketListBlock()

            if !isCollapsed {
                ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                    row(item)
                        .pocketCard(.of(index: index, count: group.items.count))
                }
            }
        }
    }

    private func row(_ item: HistoryItem) -> some View {
        let write = TransactionRowWrite.of(writes[item.id])
        return TransactionRow(
            content: .of(item, lookups: lookups), isBusy: write.isBusy, notice: write.notice,
            noticeAction: write.remedy.map { remedy in
                .init(title: TransactionsCopy.remedyTitle(remedy)) {
                    switch remedy {
                    case .retry: onToggleStatus(item)
                    case .refresh: onRefresh()
                    }
                }
            },
            onToggleStatus: { onToggleStatus(item) }
        )
    }

    @ViewBuilder
    private var footer: some View {
        if state.mode != .lista {
            HStack {
                Text(TransactionsCopy.total)
                    .pocketFont(PocketFont.subtitle)
                    .foregroundStyle(PocketColor.labelSecondary)
                Spacer(minLength: 8)
                Text(PocketFormat.currency(board.total.amount, signed: false))
                    .pocketFont(PocketFont.subtitle, tabularFigures: true)
                    .fontWeight(.semibold)
                    .foregroundStyle(PocketColor.label)
            }
            .padding(PocketMetrics.footerPadding)
            .accessibilityElement(children: .combine)
            .pocketListBlock()
        }
    }
}

#if DEBUG
enum TransactionsPreview {
    static let ref = RefYearMonth.current

    private static let first = CalendarDay.first(of: ref)
    private static let earlier = CalendarDay.first(of: ref.previous())

    private static func tag(_ id: String, _ name: String, _ context: String, _ color: UInt32?) -> Tag {
        Tag(id: TagID(rawValue: id), name: name, kind: .expense, contextId: ContextID(rawValue: context), color: color)
    }

    static func lookups(failed: Set<LookupKind> = []) -> LookupSet {
        LookupSet(
            categories: [
                TagContext(id: ContextID(rawValue: "home"), name: "Casa", color: 0xFF5B8DEF),
                TagContext(id: ContextID(rawValue: "food"), name: "Alimentação", color: 0xFFE8A33D),
            ],
            tags: [
                tag("rent", "Aluguel", "home", 0xFF5B8DEF),
                tag("market", "Mercado", "food", 0xFFE8A33D),
                tag("out", "Restaurante", "food", nil),
            ],
            cards: [CreditCard(id: CardID(rawValue: "nu"), name: "Cartão Nubank", brand: nil, closingDay: nil, color: nil)],
            failed: failed
        )
    }

    private static func item(
        _ id: String, _ name: String, _ cents: Int, on date: CalendarDay, tags: [String]? = nil,
        status: PaymentStatus = .paid, method: PaymentMethod? = nil, card: String? = nil, fixo: Bool = false
    ) -> HistoryItem {
        HistoryItem(
            id: TransactionID(rawValue: id), ref: ref, date: date, amount: Money(Decimal(cents) / 100),
            type: cents < 0 ? .expense : .income, tagIds: tags?.map { TagID(rawValue: $0) }, statusPayment: status,
            paymentMethod: method, cardId: card.map { CardID(rawValue: $0) }, seriesId: fixo ? "s" : nil, name: name)
    }

    static let items = [
        item("a", "Aluguel", -185_000, on: first, tags: ["rent"], status: .pending, method: .pix, fixo: true),
        item("b", "Mercado do bairro com um nome bastante comprido", -18_490, on: first, tags: ["market", "out"], method: .credit, card: "nu"),
        item("c", "Restaurante", -6_450, on: first, tags: ["out"], method: .debit),
        item("d", "Café", -1_200, on: earlier),
        item("e", "Salário", 720_000, on: earlier, method: .pix),
        item("f", "Freela", 150_000, on: earlier, status: .pending),
    ]

    static func ledger(_ items: [HistoryItem] = items, failed: Set<LookupKind> = []) -> MonthLedger {
        MonthLedger(ref: ref, items: items, lookups: lookups(failed: failed))
    }

    @MainActor static func screen(
        _ phase: LoadPhase<MonthLedger>, kind: TransactionType = .expense, mode: LedgerGroupMode = .lista,
        query: String = "", collapsed: Set<LedgerGroupIdentity> = [],
        writes: [TransactionID: PaymentStatusWrite] = [:]
    ) -> some View {
        var state = TransactionsViewState(kind: kind, mode: mode, query: query)
        collapsed.forEach { state.toggle($0) }
        return NavigationStack {
            List {
                LoadRegionRows(phase: phase, placeholder: { .placeholder(for: ref) }, onRetry: {}) { value in
                    TransactionsView(
                        board: .from(value, filter: state.filter, mode: mode),
                        lookups: value.lookups, state: state, writes: writes, onAction: { _ in },
                        onToggleStatus: { _ in }, onRefresh: {})
                }
            }
            .listStyle(.plain)
            .scrollContentBackground(.hidden)
            .background(PocketColor.background)
            .listSectionSeparator(.hidden)
            .environment(\.defaultMinListRowHeight, 0)
            .navigationTitle("Transações")
        }
    }
}

#Preview("Lista") { TransactionsPreview.screen(.loaded(TransactionsPreview.ledger())) }

#Preview("Por categoria") {
    TransactionsPreview.screen(.loaded(TransactionsPreview.ledger()), mode: .categoria)
}

#Preview("Por tag, recolhido") {
    TransactionsPreview.screen(
        .loaded(TransactionsPreview.ledger()), mode: .tag, collapsed: [.tag(TagID(rawValue: "market"))])
}

#Preview("Receitas") { TransactionsPreview.screen(.loaded(TransactionsPreview.ledger()), kind: .income) }

#Preview("Mês sem despesas") {
    TransactionsPreview.screen(.loaded(TransactionsPreview.ledger(Array(TransactionsPreview.items.suffix(2)))))
}

#Preview("Nenhum resultado") {
    TransactionsPreview.screen(.loaded(TransactionsPreview.ledger()), query: "zzz")
}

#Preview("Gravando") {
    TransactionsPreview.screen(
        .loaded(TransactionsPreview.ledger()),
        writes: [.init(rawValue: "a"): .init(ref: TransactionsPreview.ref, phase: .inFlight(.paid))])
}

#Preview("Falha ao gravar") {
    TransactionsPreview.screen(
        .loaded(TransactionsPreview.ledger()),
        writes: [.init(rawValue: "a"): .init(ref: TransactionsPreview.ref, phase: .failed(.unreachable))])
}

#Preview("Carregando") { TransactionsPreview.screen(.firstLoad) }

#Preview("Falhou") { TransactionsPreview.screen(.failed(.unreachable)) }

#Preview("Dados antigos") {
    TransactionsPreview.screen(.stale(TransactionsPreview.ledger(), .server))
}
#endif
