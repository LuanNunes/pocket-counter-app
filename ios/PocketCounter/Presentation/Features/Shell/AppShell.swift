import SwiftUI

/// The signed-in app. The ledger model is built here, never in `PocketCounterApp`, so it dies
/// with the session instead of holding the previous user's months.
struct AppShell: View {
    let user: AuthenticatedUser
    let signOutFailed: Bool
    let onSignOut: () -> Void

    @State private var ledger: MonthLedgerModel
    @State private var tab: TabRoute = .inicio

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
        TabView(selection: $tab) {
            Tab("Início", systemImage: "house.fill", value: TabRoute.inicio) {
                NavigationStack {
                    HomeScreen(ledger: ledger, onSelectTab: { tab = $0 })
                        .navigationDestination(for: HomeRoute.self) { homeDestination($0) }
                }
            }
            Tab("Transações", systemImage: "list.bullet", value: TabRoute.transacoes) {
                NavigationStack { transactions }
            }
            Tab("Cartões", systemImage: "creditcard", value: TabRoute.cartoes) {
                NavigationStack { notBuilt("Cartões") }
            }
            Tab("Mais", systemImage: "ellipsis.circle", value: TabRoute.mais) {
                NavigationStack {
                    more.navigationDestination(for: MoreRoute.self) { moreDestination($0) }
                }
            }
        }
        .tint(PocketColor.tint)
        .task(id: ledger.state.month) { await ledger.load() }
    }

    @ViewBuilder
    private func homeDestination(_ route: HomeRoute) -> some View {
        Group {
            switch route {
            case .report: reportScreen()
            }
        }
        .toolbar(.hidden, for: .tabBar)
    }

    @ViewBuilder
    private func moreDestination(_ route: MoreRoute) -> some View {
        Group {
            switch route {
            case .report: reportScreen()
            case .categories: notBuilt("Categorias & Tags")
            case .shortcuts: notBuilt("Atalhos")
            #if DEBUG
            case .gallery: DesignSystemGallery().navigationTitle("Design system")
            #endif
            }
        }
        .toolbar(.hidden, for: .tabBar)
    }

    @ViewBuilder
    private func reportScreen() -> some View { notBuilt("Relatório") }

    private func notBuilt(_ title: String) -> some View {
        Screen(title: title) {
            ContentUnavailableView(
                "Em construção", systemImage: "hammer", description: Text("Esta tela ainda não foi construída."))
        }
    }

    private var transactions: some View {
        TransactionsScreen(ledger: ledger)
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

    private func link(_ title: String, to destination: MoreRoute) -> some View {
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
