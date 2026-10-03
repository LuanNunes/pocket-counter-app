import SwiftUI

/// Root of the app.
///
/// Scaffolding: it shows the design system against the real environment configuration. It
/// becomes the session gate — splash / login / tab shell — once `SessionStore` exists.
struct AppRoot: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                hero

                PocketListSection(header: "Transações", accessory: ("Ver tudo", {})) {
                    PocketRow(title: "Mercado", subtitle: "Cartão · crédito") {
                        amount(Decimal(string: "-184.90")!, color: PocketColor.expense)
                    }
                    PocketRowSeparator()
                    PocketRow(title: "Salário", subtitle: "Pix") {
                        amount(Decimal(string: "7200")!, color: PocketColor.income)
                    }
                    PocketRowSeparator()
                    PocketRow(title: "Fatura Nubank", subtitle: "vence em 3 dias") {
                        amount(Decimal(string: "-1240.55")!, color: PocketColor.warning)
                    }
                }

                PocketListSection(header: "Ambiente") {
                    PocketRow(title: "Configuração", subtitle: AppEnvironment.baseURL.absoluteString) {
                        Text(AppEnvironment.name.rawValue)
                            .font(PocketFont.rowSubtitle)
                            .foregroundStyle(PocketColor.tintInk)
                            .padding(.horizontal, 7)
                            .padding(.vertical, 2)
                            .background(PocketColor.tintSoft, in: .rect(cornerRadius: 8))
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
                .font(PocketFont.heroLabel)
                .opacity(0.75)

            Text(PocketFormat.currency(Decimal(string: "5774.55")!))
                .pocketFont(PocketFont.heroValue, tracking: PocketFont.heroValueTracking, tabularFigures: true)
                .padding(.top, 2)
                .padding(.bottom, 12)

            ForEach(kpis, id: \.label) { kpi in
                HStack(spacing: 8) {
                    Circle()
                        .fill(kpi.color)
                        .frame(width: 8, height: 8)

                    Text(kpi.label)

                    Spacer(minLength: 8)

                    Text(PocketFormat.currency(kpi.value, signed: false))
                        .pocketFont(.system(.callout, weight: .semibold), tabularFigures: true)
                }
                .font(PocketFont.largeTitleSubtitle)
                .padding(.vertical, 10)
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(PocketColor.onHero.opacity(0.16))
                        .frame(height: 0.5)
                }
            }
        }
        .padding(PocketMetrics.heroPadding)
        .frame(maxWidth: .infinity, alignment: .leading)
        .foregroundStyle(PocketColor.onHero)
        .background {
            PocketColor.heroSurface
                .clipShape(.rect(cornerRadius: PocketMetrics.heroRadius))
        }
        .shadow(color: PocketColor.heroShadow, radius: 20, x: 0, y: 18)
        .padding(.horizontal, PocketMetrics.screenMargin)
    }

    private var kpis: [(label: String, value: Decimal, color: Color)] {
        [
            ("Despesas", Decimal(string: "3425.45")!, PocketColor.HeroKPI.expense),
            ("Receitas", Decimal(string: "9200.00")!, PocketColor.HeroKPI.income),
            ("Pendente", Decimal(string: "1240.55")!, PocketColor.HeroKPI.warning),
        ]
    }

    private func amount(_ value: Decimal, color: Color) -> some View {
        Text(PocketFormat.currency(value))
            .pocketFont(PocketFont.body, tabularFigures: true)
            .foregroundStyle(color)
    }
}

#Preview("Light") { AppRoot() }

#Preview("Dark") { AppRoot().preferredColorScheme(.dark) }
