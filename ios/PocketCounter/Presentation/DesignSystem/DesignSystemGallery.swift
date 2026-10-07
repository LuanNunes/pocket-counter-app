import SwiftUI

#if DEBUG

/// Showcase of the design system, for previews only. Reads no configuration.
struct DesignSystemGallery: View {
    private struct Entry: Identifiable {
        let id: String
        let title: String
        let subtitle: String
        let value: Decimal
        let kind: PocketAmount.Kind
    }

    private let entries = [
        Entry(id: "groceries", title: "Mercado", subtitle: "Cartão · crédito",
              value: PreviewMoney.groceries, kind: .expense),
        Entry(id: "salary", title: "Salário", subtitle: "Pix",
              value: PreviewMoney.salary, kind: .income),
        Entry(id: "card", title: "Fatura Nubank", subtitle: "vence em 3 dias",
              value: PreviewMoney.cardBill, kind: .pending),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                hero

                PocketList(header: "Transações", linkTitle: "Ver tudo", linkAction: {}, items: entries) { entry in
                    PocketRow(title: entry.title, subtitle: entry.subtitle) {
                        PocketAmount(value: entry.value, kind: entry.kind)
                    }
                }

                PocketListSection(header: "Ambiente") {
                    PocketRow(title: "Configuração", subtitle: "https://api-dev.pocket-counter.com/") {
                        Text("dev")
                            .pocketFont(PocketFont.caption)
                            .foregroundStyle(PocketColor.tintInk)
                            .padding(PocketMetrics.badgePadding)
                            .background(PocketColor.tintSoft, in: .rect(cornerRadius: PocketMetrics.badgeRadius))
                    }
                }

                monthPills
                PocketEmptyNote(text: "Nenhuma transação neste mês")
                notices
                loadRegions
            }
            .padding(.vertical, 20)
        }
        .background(PocketColor.background)
    }

    private var monthPills: some View {
        VStack(spacing: 0) {
            MonthPill(month: .current, isCurrent: true, canGoBack: true, canGoForward: true,
                      onPrevious: {}, onNext: {})
            MonthPill(month: .current.previous(), isCurrent: false, canGoBack: true, canGoForward: true,
                      onPrevious: {}, onNext: {})
            MonthPill(month: .january(of: RefYearMonth.current.year - 1), isCurrent: false,
                      canGoBack: false, canGoForward: true, onPrevious: {}, onNext: {})
        }
    }

    private var notices: some View {
        VStack(spacing: 12) {
            PocketNoticeCard(
                notice: PocketNotice(kind: .error, title: "Algo deu errado", detail: "Mostrando os dados anteriores."),
                action: .init(title: "Tentar novamente") {})
            if let degraded = LoadFailureMessage.degraded([.tags]) {
                PocketNoticeCard(notice: degraded)
            }
        }
    }

    private var loadRegions: some View {
        let ref = RefYearMonth.current
        let ledger = MonthLedger.placeholder(for: ref)
        let phases: [(String, LoadPhase<MonthLedger>)] = [
            ("firstLoad", .firstLoad), ("failed", .failed(.unreachable)),
            ("loaded", .loaded(ledger)), ("stale", .stale(ledger, .server)),
        ]
        return VStack(alignment: .leading, spacing: 8) {
            ForEach(phases, id: \.0) { label, phase in
                Text(label)
                    .pocketFont(PocketFont.eyebrow)
                    .textCase(.uppercase)
                    .foregroundStyle(PocketColor.labelSecondary)
                    .padding(.horizontal, PocketMetrics.sectionHeaderPadding.leading)

                LoadRegion(phase: phase, placeholder: { ledger }, onRetry: {}) { value in
                    PocketListSection(header: "Resumo do mês") {
                        PocketRow(title: "Lançamentos") { Text(value.items.count, format: .number) }
                        PocketRowSeparator()
                        PocketRow(title: "Pendente") {
                            PocketAmount(value: value.kpis.pendingTotal.amount, kind: .pending)
                        }
                    }
                }
            }
        }
    }

    private var hero: some View {
        HomeHeroCard(summary: .from(.placeholder(for: .current)))
    }
}

#Preview("Light") { DesignSystemGallery() }

#Preview("Dark") { DesignSystemGallery().preferredColorScheme(.dark) }
#endif
