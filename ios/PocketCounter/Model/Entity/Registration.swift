import Foundation

/// Sign-up input. The length rule applies here because this is where the password is chosen.
struct Registration: Hashable, Sendable {
    static let minimumPasswordLength = 8

    let name: String
    let email: String
    let password: String

    init(name: String, email: String, password: String) throws {
        guard !name.isBlank, !email.isBlank, !password.isBlank else { throw CredentialsError.emptyFields }
        guard password.count >= Self.minimumPasswordLength else {
            throw CredentialsError.passwordTooShort(minimum: Self.minimumPasswordLength)
        }
        self.name = name
        self.email = email
        self.password = password
    }
}
