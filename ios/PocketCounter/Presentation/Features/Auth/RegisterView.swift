import SwiftUI

/// Pure function of `State`. The toolbar is left to the system: on iOS 26 the inline title and
/// back chevron float in Liquid Glass over the gradient, which is glass used correctly.
struct RegisterView: View {
    let state: RegisterModel.State
    let onAction: (RegisterFormAction) -> Void

    private enum Field { case name, email, password }
    @FocusState private var focus: Field?

    var body: some View {
        AuthScaffold {
            VStack(alignment: .leading, spacing: 0) {
                Text("Criar conta")
                    .pocketFont(PocketFont.largeTitle)
                    .foregroundStyle(PocketColor.onHero)

                Text("Crie sua conta para começar")
                    .pocketFont(PocketFont.subtitle)
                    .foregroundStyle(PocketColor.onHero.opacity(0.75))
                    .padding(.top, 4)

                fieldGroup.padding(.top, 28)

                // Always visible, not an error: it is what makes a disabled button explain itself.
                Text("Pelo menos \(Registration.minimumPasswordLength) caracteres")
                    .pocketFont(PocketFont.caption)
                    .foregroundStyle(PocketColor.onHero.opacity(0.75))
                    .padding(.top, 8)

                if let message = state.message {
                    PocketInlineMessage(
                        kind: message.displayKind,
                        text: message.text,
                        secondary: message.secondary
                    )
                    .padding(.top, 12)
                }

                PocketPrimaryButton(
                    "Criar conta",
                    role: .onHero,
                    isLoading: state.isSubmitting
                ) {
                    focus = nil
                    onAction(.submit)
                }
                .disabled(!state.canSubmit)
                .padding(.top, 20)

                Spacer(minLength: 24)
            }
        }
        .navigationTitle("Criar conta")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var fieldGroup: some View {
        VStack(spacing: 0) {
            PocketFieldRow(
                systemImage: "person",
                prompt: "Nome",
                text: binding(state.name) { .nameChanged($0) },
                accessibilityLabel: "Nome"
            )
            .focused($focus, equals: .name)
            .textContentType(.name)
            .textInputAutocapitalization(.words)
            .submitLabel(.next)
            .onSubmit { focus = .email }

            PocketRowSeparator()

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
            // The backend's only rule is length ≥ 8, which iOS's generated passwords satisfy.
            .textContentType(.newPassword)
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

    private func binding(
        _ value: String,
        _ action: @escaping (String) -> RegisterFormAction
    ) -> Binding<String> {
        Binding(get: { value }, set: { onAction(action($0)) })
    }
}

#if DEBUG
#Preview("Empty") {
    NavigationStack { RegisterView(state: .init()) { _ in } }
}

#Preview("Ready") {
    NavigationStack {
        RegisterView(state: .init(name: "Ana", email: "ana@b.com", password: "segredo12")) { _ in }
    }
}

#Preview("Submitting") {
    NavigationStack {
        RegisterView(
            state: .init(name: "Ana", email: "ana@b.com", password: "segredo12", isSubmitting: true)
        ) { _ in }
    }
}

#Preview("E-mail already taken") {
    NavigationStack {
        RegisterView(
            state: .init(
                name: "Ana",
                email: "ana@b.com",
                password: "segredo12",
                message: AuthMessage(kind: .error, text: "Este e-mail já está cadastrado")
            )
        ) { _ in }
    }
}
#endif
