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

/// Validated login or registration input. `name` is set only for registration.
struct Credentials: Hashable, Sendable {
    static let minimumPasswordLength = 8

    let name: String?
    let email: String
    let password: String

    init(email: String, password: String) throws {
        try self.init(name: nil, email: email, password: password)
    }

    init(name: String, email: String, password: String) throws {
        try self.init(name: Optional(name), email: email, password: password)
    }

    private init(name: String?, email: String, password: String) throws {
        let required = [name, email, password].compactMap { $0 }
        guard required.allSatisfy({ !$0.isBlank }) else { throw CredentialsError.emptyFields }
        guard password.count >= Self.minimumPasswordLength else {
            throw CredentialsError.passwordTooShort(minimum: Self.minimumPasswordLength)
        }
        self.name = name
        self.email = email
        self.password = password
    }
}

private extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
}
