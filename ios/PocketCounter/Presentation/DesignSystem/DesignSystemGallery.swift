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
            }
            .padding(.vertical, 20)
        }
        .background(PocketColor.background)
    }

    private var hero: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Saldo de \(PocketFormat.monthLabel(year: 2026, month: 10))")
                .pocketFont(PocketFont.heroLabel)
                .opacity(0.75)

            Text(PocketFormat.currency(PreviewMoney.balance))
                .heroValueFont()
                .padding(.top, 2)
                .padding(.bottom, 12)

            ForEach(kpis) { kpi in
                HStack(spacing: 8) {
                    Circle()
                        .fill(kpi.color)
                        .frame(width: PocketMetrics.heroKpiDot, height: PocketMetrics.heroKpiDot)

                    Text(kpi.label)
                        .pocketFont(PocketFont.heroKpi)

                    Spacer(minLength: 8)

                    Text(PocketFormat.currency(kpi.value, signed: false))
                        .pocketFont(PocketFont.heroKpiValue, tabularFigures: true)
                }
                .padding(.vertical, PocketMetrics.heroKpiPaddingV)
                .accessibilityElement(children: .combine)
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(PocketColor.onHero.opacity(0.16))
                        .frame(height: PocketMetrics.hairline)
                }
            }
        }
        .padding(PocketMetrics.heroPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .foregroundStyle(PocketColor.onHero)
        .background {
            PocketColor.heroSurface
                .clipShape(.rect(cornerRadius: PocketMetrics.heroRadius))
                .shadow(color: PocketColor.heroShadow, radius: 20, x: 0, y: 18)
        }
        .padding(.horizontal, PocketMetrics.screenMargin)
    }

    private struct Kpi: Identifiable {
        let id: String
        let label: String
        let value: Decimal
        let color: Color
    }

    private var kpis: [Kpi] {
        [
            Kpi(id: "expenses", label: "Despesas", value: PreviewMoney.expenses, color: PocketColor.onHeroExpense),
            Kpi(id: "incomes", label: "Receitas", value: PreviewMoney.incomes, color: PocketColor.onHeroIncome),
            Kpi(id: "pending", label: "Pendente", value: PreviewMoney.pending, color: PocketColor.onHeroWarning),
        ]
    }
}

#Preview("Light") { DesignSystemGallery() }

#Preview("Dark") { DesignSystemGallery().preferredColorScheme(.dark) }
#endif
