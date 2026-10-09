import SwiftUI

struct HomeView: View {
    let summary: HomeSummary
    let onAction: (HomeAction) -> Void
    let onQuickAdd: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            QuickAddHomeField(onOpen: onQuickAdd)
            HomeHeroCard(summary: summary)
            HomeTiles(summary: summary, onAction: onAction)
                .padding(.vertical, PocketMetrics.tilesMarginV)
            HomeTransactionsCue(count: summary.transactionCount, onAction: onAction)
        }
    }
}

private struct HomeTransactionsCue: View {
    let count: Int
    let onAction: (HomeAction) -> Void

    var body: some View {
        PocketListSection {
            Button { onAction(.showTransactions) } label: {
                PocketRow(title: "Lançamentos", subtitle: count == 0 ? "Nenhum neste mês" : HomeCopy.transactionCount(count)) {
                    Image(systemName: "chevron.right")
                        .pocketFont(PocketFont.caption)
                        .foregroundStyle(PocketColor.labelTertiary)
                }
                .contentShape(.rect)
            }
            .buttonStyle(PocketCardButtonStyle(radius: PocketMetrics.listRadius))
        }
    }
}

#if DEBUG
@MainActor
private enum HomePreview {
    static let ref = RefYearMonth.current

    static func ledger(items: [HistoryItem], failed: Set<LookupKind> = []) -> MonthLedger {
        let card = CreditCard(id: CardID(rawValue: "c1"), name: "Nubank", brand: nil, closingDay: nil, color: nil)
        return MonthLedger(ref: ref, items: items, lookups: LookupSet(categories: [], tags: [], cards: [card], failed: failed))
    }

    static func item(_ id: String, _ cents: Int, _ status: PaymentStatus = .paid, invoice: Bool = false) -> HistoryItem {
        HistoryItem(
            id: TransactionID(rawValue: id), ref: ref, date: .first(of: ref), amount: Money(Decimal(cents) / 100),
            type: cents < 0 ? .expense : .income, tagIds: nil, statusPayment: status,
            cardId: invoice ? CardID(rawValue: "c1") : nil, name: id, isInvoice: invoice)
    }

    static let items = [
        item("a", 920_000), item("b", -342_545), item("c", -124_055, .pending, invoice: true),
    ]

    static func screen(_ phase: LoadPhase<MonthLedger>) -> some View {
        NavigationStack {
            Screen(title: "Início") {
                LoadRegion(phase: phase, placeholder: { .placeholder(for: ref) }, onRetry: {}) { value in
                    HomeView(summary: .from(value), onAction: { _ in }, onQuickAdd: {})
                }
            }
        }
    }
}

#Preview("Loaded") { HomePreview.screen(.loaded(HomePreview.ledger(items: HomePreview.items))) }

#Preview("Empty month") { HomePreview.screen(.loaded(HomePreview.ledger(items: []))) }

#Preview("Cards lookup failed") {
    HomePreview.screen(.loaded(HomePreview.ledger(items: HomePreview.items, failed: [.cards])))
}

#Preview("Failed") { HomePreview.screen(.failed(.unreachable)) }

#Preview("Stale") {
    HomePreview.screen(.stale(HomePreview.ledger(items: HomePreview.items), .server))
}
#endif
