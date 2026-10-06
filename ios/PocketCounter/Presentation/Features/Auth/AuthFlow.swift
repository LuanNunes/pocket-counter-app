import SwiftUI

/// The signed-out stack. Cadastro is pushed, not presented: registration replaces the goal
/// rather than returning to Login, and a push keeps the gradient continuous.
///
/// On success the gate swaps to the shell and this whole stack goes away — it never pops first.
struct AuthFlow: View {
    private let register: RegisterAction
    private let onBack: (() -> Void)?

    private enum Route: Hashable { case register }

    @State private var path: [Route] = []
    @State private var loginModel: LoginModel

    /// `onBack` is non-nil only when Login was reached through the splash's password escape.
    init(signIn: @escaping SignInAction, register: @escaping RegisterAction, onBack: (() -> Void)? = nil) {
        _loginModel = State(initialValue: LoginModel(signIn: signIn))
        self.register = register
        self.onBack = onBack
    }

    var body: some View {
        NavigationStack(path: $path) {
            LoginScreen(model: loginModel, onRegister: { path.append(.register) }, onBack: onBack)
                .navigationDestination(for: Route.self) { _ in
                    RegisterScreen(register: register) { email in
                        loginModel.seed(email: email)
                        path.removeAll()
                    }
                }
        }
    }
}

struct RegisterScreen: View {
    @State private var model: RegisterModel

    private let onSignIn: (String) -> Void

    init(register: @escaping RegisterAction, onSignIn: @escaping (String) -> Void) {
        _model = State(initialValue: RegisterModel(register: register))
        self.onSignIn = onSignIn
    }

    var body: some View {
        RegisterView(state: model.state) { action in
            // Popping to Login is navigation, which the model has no opinion about.
            guard action != .signInWithExistingAccount else { return onSignIn(model.state.email) }
            model.handle(action)
        }
        .onDisappear { model.cancel() }
    }
}

#if DEBUG
#Preview {
    AuthFlow(signIn: { _ in }, register: { _ in })
}
#endif
