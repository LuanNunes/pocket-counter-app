import SwiftUI

/// What Login and Cadastro share: the hero background, the forced dark appearance, the scroll
/// container, the screen margin and the keyboard behaviour.
///
/// The forced dark lives here and nowhere else, so the appearance has one owner. It is not a
/// style choice: `PocketColor.heroSurface` is fixed dark in both appearances, so "light" has no
/// meaning on these screens, and forcing it is what makes the native chrome agree — the keyboard,
/// the AutoFill sheet, a presented alert. `.environment(\.colorScheme, .dark)` does not reach
/// any of those.
///
/// Only white content belongs above ~55% of screen height, where the gradient's highlight still
/// has alpha; non-white inks and strokes are measured against `heroBase` and hold only below it.
struct AuthScaffold<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            PocketColor.heroSurface
                .ignoresSafeArea()

            ScrollView {
                content
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, PocketMetrics.screenMargin)
            }
            .scrollDismissesKeyboard(.interactively)
            .scrollBounceBehavior(.basedOnSize)
        }
        .preferredColorScheme(.dark)
        .tint(PocketColor.tint)
    }
}

#if DEBUG
#Preview("Scaffold") {
    @Previewable @State var email = ""
    @Previewable @State var password = ""

    AuthScaffold {
        VStack(alignment: .leading, spacing: 0) {
            Spacer(minLength: PocketMetrics.formContentInset)

            AuthBrandMark(.compact)

            Text("Entrar")
                .pocketFont(PocketFont.largeTitle)
                .foregroundStyle(PocketColor.onHero)
                .accessibilityAddTraits(.isHeader)
                .padding(.top, 24)

            Text("Faça login para continuar")
                .pocketFont(PocketFont.subtitle)
                .foregroundStyle(PocketColor.onHero.opacity(0.75))
                .padding(.top, 4)

            VStack(spacing: 0) {
                PocketFieldRow(systemImage: "envelope", prompt: "E-mail", text: $email,
                               accessibilityLabel: "E-mail")

                Rectangle()
                    .fill(PocketColor.onHero.opacity(0.16))
                    .frame(height: PocketMetrics.hairline)
                    .padding(.leading, PocketMetrics.rowPaddingH)

                PocketFieldRow(systemImage: "lock", prompt: "Senha", text: $password,
                               isSecure: true, accessibilityLabel: "Senha")
            }
            .clipShape(.rect(cornerRadius: PocketMetrics.listRadius, style: .continuous))
            .overlay {
                RoundedRectangle(cornerRadius: PocketMetrics.listRadius, style: .continuous)
                    .strokeBorder(PocketColor.onHero.opacity(0.22), lineWidth: 1)
            }
            .padding(.top, 28)

            PocketPrimaryButton("Entrar", role: .onHero) {}
                .padding(.top, 20)

            Spacer(minLength: 24)
        }
    }
}
#endif
