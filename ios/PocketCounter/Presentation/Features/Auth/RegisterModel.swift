import Foundation

typealias RegisterAction = @MainActor (Registration) async throws(AuthenticationFailure) -> Void

enum RegisterFormAction: Equatable {
    case nameChanged(String)
    case emailChanged(String)
    case passwordChanged(String)
    case submit
    case signInWithExistingAccount
}

@MainActor
@Observable
final class RegisterModel {
    struct State: Equatable {
        var name = ""
        var email = ""
        var password = ""
        var isSubmitting = false
        /// `nil` renders nothing.
        var message: AuthMessage?

        /// Asks the entity, so the button and `submit()` can never disagree about the rule.
        var canSubmit: Bool {
            do {
                _ = try Registration(name: name, email: email, password: password)
                return true
            } catch {
                return false
            }
        }
    }

    private(set) var state = State()
    private let register: RegisterAction
    private var inFlight: Task<Void, Never>?

    init(register: @escaping RegisterAction) {
        self.register = register
    }

    func handle(_ action: RegisterFormAction) {
        switch action {
        case .nameChanged(let name):
            state.name = name
        case .emailChanged(let email):
            state.email = email
        case .passwordChanged(let password):
            state.password = password
        case .submit:
            inFlight = Task { await submit() }
        case .signInWithExistingAccount:
            break
        }
    }

    /// Changes no state: the `defer` in `submit()` clears the busy flag, and no failure renders.
    /// A registration already sent still lands: the account exists and `SessionModel` signs it in.
    func cancel() {
        inFlight?.cancel()
        inFlight = nil
    }

    func submit() async {
        guard !state.isSubmitting, !Task.isCancelled else { return }
        state.message = nil
        state.isSubmitting = true
        defer { state.isSubmitting = false }

        let registration: Registration
        do {
            registration = try Registration(name: state.name, email: state.email, password: state.password)
        } catch {
            state.message = AuthMessage(kind: .error, text: error.message)
            return
        }
        do {
            try await register(registration)
        } catch {
            guard !Task.isCancelled else { return }
            state.message = AuthFailureMessage.text(for: error, path: .register)
        }
    }
}
