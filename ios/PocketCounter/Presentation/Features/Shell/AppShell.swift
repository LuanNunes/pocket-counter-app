import SwiftUI

/// The signed-in app. The ledger model is built here, never in `PocketCounterApp`, so it dies
/// with the session instead of holding the previous user's months.
struct AppShell: View {
    private enum Destination: Hashable {
        case report, categories, shortcuts
        #if DEBUG
        case gallery
        #endif
    }

    let user: AuthenticatedUser
    let signOutFailed: Bool
    let onSignOut: () -> Void

    @State private var ledger: MonthLedgerModel

    init(
        container: AppContainer,
        user: AuthenticatedUser,
        signOutFailed: Bool,
        onSessionExpired: @escaping SessionExpiredAction,
        onSignOut: @escaping () -> Void
    ) {
        self.user = user
        self.signOutFailed = signOutFailed
        self.onSignOut = onSignOut
        _ledger = State(wrappedValue: MonthLedgerModel(
            loadMonth: container.loadMonth, onSessionExpired: onSessionExpired))
    }

    var body: some View {
        TabView {
            Tab("Início", systemImage: "house.fill") {
                stack {
                    monthScreen("Início", summary: summaryRows)
                }
            }
            Tab("Transações", systemImage: "list.bullet") {
                stack { monthScreen("Transações", summary: countRow) }
            }
            Tab("Cartões", systemImage: "creditcard") {
                stack { notBuilt("Cartões") }
            }
            Tab("Mais", systemImage: "ellipsis.circle") {
                stack { more }
            }
        }
        .tint(PocketColor.tint)
    }

    private func stack(@ViewBuilder _ root: () -> some View) -> some View {
        NavigationStack {
            root()
                .navigationDestination(for: Destination.self) { destination($0) }
        }
    }

    @ViewBuilder
    private func destination(_ destination: Destination) -> some View {
        Group {
            switch destination {
            case .report: notBuilt("Relatório")
            case .categories: notBuilt("Categorias & Tags")
            case .shortcuts: notBuilt("Atalhos")
            #if DEBUG
            case .gallery: DesignSystemGallery().navigationTitle("Design system")
            #endif
            }
        }
        .toolbar(.hidden, for: .tabBar)
    }

    private func notBuilt(_ title: String) -> some View {
        Screen(title: title) {
            ContentUnavailableView(
                "Em construção", systemImage: "hammer", description: Text("Esta tela ainda não foi construída."))
        }
    }

    // MARK: Início and Transações — throwaway bodies, replaced in Fase 3

    private func monthScreen(
        _ title: String,
        summary: @escaping (MonthLedger) -> some View
    ) -> some View {
        let state = ledger.state
        return Screen(title: title, onRefresh: { await ledger.refresh() }) {
            MonthPill(
                month: state.month, isCurrent: state.month == .current,
                canGoBack: state.canSelectPrevious, canGoForward: state.canSelectNext,
                onPrevious: { ledger.selectPrevious() }, onNext: { ledger.selectNext() }
            )
            LoadRegion(
                phase: state.load.phase, placeholder: { .placeholder(for: state.month) },
                isRetrying: state.load.isLoading, onRetry: { Task { await ledger.refresh() } }
            ) { value in
                VStack(alignment: .leading, spacing: PocketMetrics.tileSpacing) {
                    if let degraded = LoadFailureMessage.degraded(value.lookups.failed) {
                        PocketNoticeCard(notice: degraded)
                    }
                    PocketListSection(header: "Resumo do mês") { summary(value) }
                }
            }
        }
        .task(id: state.month) { await ledger.load() }
    }

    private func summaryRows(_ value: MonthLedger) -> some View {
        let kpis = value.kpis
        let balance = kpis.totals.balance.amount
        return VStack(spacing: 0) {
            PocketRow(title: "Saldo") { PocketAmount(value: balance, kind: balance > 0 ? .income : .expense) }
            PocketRowSeparator()
            countRow(value)
            PocketRowSeparator()
            PocketRow(title: "Pendente", subtitle: "\(kpis.pendingCount) a pagar") {
                PocketAmount(value: kpis.pendingTotal.amount, kind: .pending)
            }
        }
    }

    private func countRow(_ value: MonthLedger) -> some View {
        PocketRow(title: "Lançamentos") { Text(value.items.count, format: .number.locale(PocketFormat.locale)).pocketFont(PocketFont.body) }
    }

    // MARK: Mais

    private var more: some View {
        Screen(title: "Mais") {
            VStack(alignment: .leading, spacing: PocketMetrics.tileSpacing) {
                PocketListSection {
                    PocketRow(title: user.displayName, subtitle: user.email)
                }

                if signOutFailed {
                    // The contract keeps the tokens on a failed clear: the user is still signed in.
                    PocketInlineMessage(
                        kind: .error, text: "Não foi possível encerrar a sessão agora", surface: .onCell
                    )
                    .padding(.horizontal, PocketMetrics.screenMargin)
                }

                PocketListSection {
                    link("Relatório", to: .report)
                    PocketRowSeparator()
                    link("Categorias & Tags", to: .categories)
                    PocketRowSeparator()
                    link("Atalhos", to: .shortcuts)
                    #if DEBUG
                    PocketRowSeparator()
                    link("Design system", to: .gallery)
                    #endif
                }

                PocketListSection {
                    Button(action: onSignOut) {
                        Text("Sair")
                            .pocketFont(PocketFont.body)
                            .foregroundStyle(PocketColor.destructiveInk)
                            .frame(maxWidth: .infinity, minHeight: PocketMetrics.rowMinHeight)
                            .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func link(_ title: String, to destination: Destination) -> some View {
        NavigationLink(value: destination) {
            PocketRow(title: title) {
                Image(systemName: "chevron.right")
                    .pocketFont(PocketFont.caption)
                    .foregroundStyle(PocketColor.labelTertiary)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }
}
