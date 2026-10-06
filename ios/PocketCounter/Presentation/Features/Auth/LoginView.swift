import SwiftUI

/// Pure function of `State`, so every failure state is previewable.
///
/// No glass anywhere: there is no scrolling content behind these controls to refract, and a glass
/// panel over a static gradient reads as a flat translucent slab.
struct LoginView: View {
    let state: LoginModel.State
    let onAction: (LoginAction) -> Void

    private enum Field { case email, password }
    @FocusState private var focus: Field?

    var body: some View {
        AuthScaffold {
            VStack(alignment: .leading, spacing: 0) {
                Spacer(minLength: 48)

                AuthBrandMark(.compact)

                Text("Entrar")
                    .pocketFont(PocketFont.largeTitle)
                    .foregroundStyle(PocketColor.onHero)
                    .padding(.top, 24)

                Text("Faça login para continuar")
                    .pocketFont(PocketFont.subtitle)
                    .foregroundStyle(PocketColor.onHero.opacity(0.75))
                    .padding(.top, 4)

                fieldGroup.padding(.top, 28)

                if let message = state.message {
                    PocketInlineMessage(
                        kind: message.displayKind,
                        text: message.text,
                        secondary: message.secondary
                    )
                    .padding(.top, 12)
                }

                PocketPrimaryButton(
                    "Entrar",
                    role: .onHero,
                    isLoading: state.isSubmitting
                ) {
                    focus = nil
                    onAction(.submit)
                }
                .padding(.top, 20)

                Button("Não tem conta? Criar conta") { onAction(.register) }
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(PocketColor.onHero)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 32)
                    .disabled(state.isSubmitting)

                Spacer(minLength: 24)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private var fieldGroup: some View {
        VStack(spacing: 0) {
            PocketFieldRow(
                systemImage: "envelope",
                prompt: "E-mail",
                text: binding(state.email) { .emailChanged($0) },
                accessibilityLabel: "E-mail"
            )
            .focused($focus, equals: .email)
            .textContentType(.emailAddress)
            .keyboardType(.emailAddress)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(.next)
            .onSubmit { focus = .password }

            PocketRowSeparator()

            PocketFieldRow(
                systemImage: "lock",
                prompt: "Senha",
                text: binding(state.password) { .passwordChanged($0) },
                isSecure: true,
                accessibilityLabel: "Senha"
            )
            .focused($focus, equals: .password)
            .textContentType(.password)
            .submitLabel(.go)
            .onSubmit {
                focus = nil
                onAction(.submit)
            }
        }
        .clipShape(.rect(cornerRadius: PocketMetrics.listRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: PocketMetrics.listRadius, style: .continuous)
                .strokeBorder(PocketColor.onHero.opacity(0.22), lineWidth: 1)
        }
        .disabled(state.isSubmitting)
    }

    /// `state` is `private(set)`, so a field cannot bind to it directly — writes go out as actions.
    private func binding(_ value: String, _ action: @escaping (String) -> LoginAction) -> Binding<String> {
        Binding(get: { value }, set: { onAction(action($0)) })
    }
}

#if DEBUG
#Preview("Empty") {
    LoginView(state: .init()) { _ in }
}

#Preview("Filled") {
    LoginView(state: .init(email: "ana@b.com", password: "segredo12")) { _ in }
}

#Preview("Submitting") {
    LoginView(state: .init(email: "ana@b.com", password: "segredo12", isSubmitting: true)) { _ in }
}

#Preview("Wrong credentials") {
    LoginView(
        state: .init(
            email: "ana@b.com",
            message: AuthMessage(kind: .error, text: "E-mail ou senha incorretos")
        )
    ) { _ in }
}

#Preview("Twice wrong") {
    LoginView(
        state: .init(
            email: "ana@b.com",
            message: AuthMessage(
                kind: .error,
                text: "E-mail ou senha incorretos",
                secondary: "A recuperação de senha ainda não está disponível neste app."
            )
        )
    ) { _ in }
}

#Preview("Offline") {
    LoginView(
        state: .init(message: AuthMessage(kind: .offline, text: "Sem conexão com o servidor"))
    ) { _ in }
}
#endif
