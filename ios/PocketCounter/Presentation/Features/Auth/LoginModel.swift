import Observation

typealias SignInAction = @MainActor (LoginCredentials) async throws(AuthenticationFailure) -> Void

enum LoginAction: Equatable {
    case emailChanged(String)
    case passwordChanged(String)
    case submit
    case register
}

/// Takes the one verb it needs rather than the `SessionModel`: a login form has no business
/// reaching `signOut()` or the rest of the lifecycle.
@MainActor
@Observable
final class LoginModel {
    struct State: Equatable {
        var email = ""
        var password = ""
        var isSubmitting = false
        /// `nil` renders nothing.
        var message: AuthMessage?
    }

    private(set) var state = State()
    private let signIn: SignInAction
    private var consecutiveInvalidCredentials = 0

    init(signIn: @escaping SignInAction) {
        self.signIn = signIn
    }

    func handle(_ action: LoginAction) {
        switch action {
        case .emailChanged(let email):
            state.email = email
        case .passwordChanged(let password):
            state.password = password
        case .submit:
            Task { await submit() }
        case .register:
            break
        }
    }

    func submit() async {
        guard !state.isSubmitting else { return }
        state.message = nil
        state.isSubmitting = true
        defer { state.isSubmitting = false }

        let credentials: LoginCredentials
        do {
            credentials = try LoginCredentials(email: state.email, password: state.password)
        } catch {
            state.message = AuthMessage(kind: .error, text: error.message)
            return
        }
        do {
            try await signIn(credentials)
        } catch {
            show(error)
        }
    }

    private func show(_ failure: AuthenticationFailure) {
        guard failure != .abandoned else { return }
        consecutiveInvalidCredentials = failure == .invalidCredentials ? consecutiveInvalidCredentials + 1 : 0
        let message = AuthFailureMessage.text(for: failure, path: .password)
        guard consecutiveInvalidCredentials >= 2, let message else {
            state.message = message
            return
        }
        state.message = AuthMessage(
            kind: message.kind, text: message.text,
            secondary: AuthFailureMessage.passwordRecoveryUnavailable, action: message.action
        )
    }
}
