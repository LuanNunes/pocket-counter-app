import Foundation

typealias RegisterAction = @MainActor (Registration) async throws(AuthenticationFailure) -> Void

enum RegisterFormAction: Equatable {
    case nameChanged(String)
    case emailChanged(String)
    case passwordChanged(String)
    case submit
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

        /// UTF-16 units, matching the backend's Kotlin `String.length`.
        var canSubmit: Bool {
            !name.isBlank && !email.isBlank
                && password.utf16.count >= Registration.minimumPasswordLength
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
        }
    }

    /// Tapping Back with a request open. The only state change is clearing the busy flag —
    /// nobody is waiting for an answer, so nothing is rendered.
    func cancel() {
        inFlight?.cancel()
        inFlight = nil
    }

    func submit() async {
        guard !state.isSubmitting else { return }
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
            state.message = AuthFailureMessage.text(for: error, path: .register)
        }
    }
}
