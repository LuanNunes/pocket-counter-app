import SwiftUI

/// The month's balance and its three figures: an opaque gradient, never glass.
struct HomeHeroCard: View {
    let summary: HomeSummary

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let totals = summary.kpis.totals
        VStack(alignment: .leading, spacing: 0) {
            balance(totals.balance.amount)
            row("Despesas", PocketColor.onHeroExpense, totals.expense, caption: HomeCopy.entryCount(summary.kpis.expenseCount),
                spoken: HomeCopy.spokenEntryCount(summary.kpis.expenseCount))
            row("Receitas", PocketColor.onHeroIncome, totals.income, caption: HomeCopy.entryCount(summary.kpis.incomeCount),
                spoken: HomeCopy.spokenEntryCount(summary.kpis.incomeCount))
            if summary.showsPending {
                row("Pendente", PocketColor.onHeroWarning, summary.kpis.pendingTotal,
                    caption: HomeCopy.pendingCount(summary.kpis.pendingCount),
                    spoken: HomeCopy.pendingCount(summary.kpis.pendingCount))
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

    private func balance(_ amount: Decimal) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Saldo do mês")
                .pocketFont(PocketFont.heroLabel)
                .opacity(0.75)

            Text(PocketFormat.currency(amount))
                .heroValueFont()
                .lineLimit(2)
                .minimumScaleFactor(0.75)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 2)
                .padding(.bottom, 12)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Saldo do mês")
        .accessibilityValue(PocketFormat.spokenCurrency(amount))
    }

    private func row(_ title: String, _ dot: Color, _ value: Money, caption: String, spoken: String) -> some View {
        let figure = Text(PocketFormat.currency(value.amount, signed: false))
            .pocketFont(PocketFont.heroKpiValue, tabularFigures: true)
        let count = Text(caption)
            .pocketFont(PocketFont.heroKpiCaption)
            .foregroundStyle(PocketColor.onHero.opacity(0.6))
        let label = Label {
            Text(title).pocketFont(PocketFont.heroKpi)
        } icon: {
            Circle()
                .fill(dot)
                .frame(width: PocketMetrics.heroKpiDot, height: PocketMetrics.heroKpiDot)
        }

        return Group {
            if typeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 0) {
                    label
                    figure
                    count
                }
            } else {
                HStack(alignment: .firstTextBaseline) {
                    label
                    Spacer(minLength: 8)
                    VStack(alignment: .trailing, spacing: 0) {
                        figure
                        count
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, PocketMetrics.heroKpiPaddingV)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(PocketColor.onHero.opacity(0.16))
                .frame(height: PocketMetrics.hairline)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title), \(PocketFormat.currency(value.amount, signed: false)), \(spoken)")
    }
}
