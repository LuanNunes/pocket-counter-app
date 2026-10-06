import SwiftUI

/// The signed-out stack. Cadastro is pushed, not presented: registration replaces the goal
/// rather than returning to Login, and a push keeps the gradient continuous.
///
/// On success the gate swaps to the shell and this whole stack goes away — it never pops first.
struct AuthFlow: View {
    let signIn: SignInAction
    let register: RegisterAction

    private enum Route: Hashable { case register }

    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            LoginScreen(signIn: signIn) { path.append(.register) }
                .navigationDestination(for: Route.self) { _ in
                    RegisterScreen(register: register)
                }
        }
    }
}

struct RegisterScreen: View {
    @State private var model: RegisterModel

    init(register: @escaping RegisterAction) {
        _model = State(initialValue: RegisterModel(register: register))
    }

    var body: some View {
        RegisterView(state: model.state) { model.handle($0) }
            .onDisappear { model.cancel() }
    }
}

#if DEBUG
#Preview {
    AuthFlow(signIn: { _ in }, register: { _ in })
}
#endif
