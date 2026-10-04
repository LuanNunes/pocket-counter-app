import Foundation

enum CredentialsError: Error, Equatable, Sendable {
    case emptyFields
    case passwordTooShort(minimum: Int)

    var message: String {
        switch self {
        case .emptyFields:
            "Preencha todos os campos"
        case .passwordTooShort(let minimum):
            "A senha deve ter pelo menos \(minimum) caracteres"
        }
    }
}

/// Sign-in input. No length rule: the server decides whether an existing password is valid.
struct LoginCredentials: Hashable, Sendable {
    let email: String
    let password: String

    init(email: String, password: String) throws {
        guard !email.isBlank, !password.isBlank else { throw CredentialsError.emptyFields }
        self.email = email
        self.password = password
    }
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
