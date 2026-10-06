import SwiftUI

/// The thinnest shell that makes the gate verifiable end to end: sign in, see your own name,
/// sign out, land back on Login. The real `TabView` shell replaces this.
struct AppShellPlaceholder: View {
    let user: AuthenticatedUser
    let signOutFailed: Bool
    let onSignOut: () -> Void

    var body: some View {
        TabView {
            Tab("Início", systemImage: "house.fill") { placeholder("Início", symbol: "house.fill") }
            Tab("Transações", systemImage: "list.bullet") { placeholder("Transações", symbol: "list.bullet") }
            Tab("Cartões", systemImage: "creditcard") { placeholder("Cartões", symbol: "creditcard") }
            Tab("Mais", systemImage: "ellipsis.circle") { more }
        }
        .tint(PocketColor.tint)
    }

    private func placeholder(_ title: String, symbol: String) -> some View {
        NavigationStack {
            ContentUnavailableView(
                "Em breve",
                systemImage: symbol,
                description: Text("Esta tela ainda não foi construída.")
            )
            .navigationTitle(title)
        }
    }

    private var more: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: PocketMetrics.rowSpacing) {
                    PocketListSection {
                        PocketRow(title: user.displayName, subtitle: user.email)
                    }

                    if signOutFailed {
                        // The contract keeps the tokens on a failed clear, so the user is still
                        // signed in — do not present them as signed out.
                        PocketInlineMessage(
                            kind: .error,
                            text: "Não foi possível encerrar a sessão agora",
                            surface: .onCell
                        )
                    }

                    PocketListSection {
                        Button("Sair", action: onSignOut)
                            .pocketFont(PocketFont.body)
                            .foregroundStyle(PocketColor.destructiveInk)
                            .frame(maxWidth: .infinity, minHeight: PocketMetrics.rowMinHeight)
                    }
                }
                .padding(.horizontal, PocketMetrics.screenMargin)
            }
            .background(PocketColor.background)
            .navigationTitle("Mais")
        }
    }
}

#if DEBUG
/// `UserID` is failable because it validates a UUID, so the literal is unwrapped here rather
/// than forced: a typo in it must leave the preview blank, never trap the whole canvas.
private let previewUser = UserID(rawValue: "7b1f0f1e-8b9c-4c2a-9a1d-3f5e6c7d8a90")
    .map { AuthenticatedUser(id: $0, name: "Ana", email: "ana@b.com") }

#Preview("Signed in") {
    if let previewUser {
        AppShellPlaceholder(user: previewUser, signOutFailed: false) {}
    }
}

#Preview("Sign-out failed") {
    if let previewUser {
        AppShellPlaceholder(user: previewUser, signOutFailed: true) {}
    }
}
#endif
