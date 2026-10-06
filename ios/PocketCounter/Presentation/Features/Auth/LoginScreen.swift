import SwiftUI

/// The only stateful wrapper around `LoginView`. The model is owned by `AuthFlow`, which is rebuilt
/// when the gate leaves and returns — that is what resets the form after a sign-out.
struct LoginScreen: View {
    let model: LoginModel
    let onRegister: () -> Void
    var onBack: (() -> Void)?

    var body: some View {
        LoginView(state: model.state, offersBack: onBack != nil) { action in
            // Navigation is the view's job; the model has no opinion about it.
            switch action {
            case .register: onRegister()
            case .back: onBack?()
            case .emailChanged, .passwordChanged, .submit: model.handle(action)
            }
        }
        .onDisappear { model.cancel() }
    }
}

#if DEBUG
#Preview {
    LoginScreen(model: LoginModel(signIn: { _ in }), onRegister: {})
}
#endif
