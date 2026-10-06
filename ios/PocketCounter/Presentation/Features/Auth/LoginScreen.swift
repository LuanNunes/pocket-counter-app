import SwiftUI

/// The only stateful wrapper around `LoginView`. Rebuilt when the gate leaves and returns, which
/// is what resets the form after a sign-out.
struct LoginScreen: View {
    @State private var model: LoginModel
    private let onRegister: () -> Void

    init(signIn: @escaping SignInAction, onRegister: @escaping () -> Void) {
        _model = State(initialValue: LoginModel(signIn: signIn))
        self.onRegister = onRegister
    }

    var body: some View {
        LoginView(state: model.state) { action in
            // Navigation is the view's job; the model has no opinion about it.
            guard action != .register else { return onRegister() }
            model.handle(action)
        }
    }
}

#if DEBUG
#Preview {
    LoginScreen(signIn: { _ in }, onRegister: {})
}
#endif
