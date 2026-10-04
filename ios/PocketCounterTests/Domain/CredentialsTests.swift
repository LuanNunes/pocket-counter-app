import Testing

@testable import PocketCounter

@Suite("LoginCredentials")
struct LoginCredentialsTests {

    @Test("valid login credentials are accepted")
    func valid() throws {
        let credentials = try LoginCredentials(email: "a@b.com", password: "secret")

        #expect(credentials.email == "a@b.com")
        #expect(credentials.password == "secret")
    }

    @Test("a blank email or password is rejected as empty fields", arguments: [
        ("", "12345678"), ("   ", "12345678"), ("a@b.com", ""), ("a@b.com", "   "),
    ])
    func blankFields(email: String, password: String) {
        #expect(throws: CredentialsError.emptyFields) {
            try LoginCredentials(email: email, password: password)
        }
    }

    @Test("a short legacy password is accepted: the server owns that policy")
    func shortPasswordAccepted() throws {
        let credentials = try LoginCredentials(email: "a@b.com", password: "1")

        #expect(credentials.password == "1")
    }

    @Test("an email without an @ is not rejected locally")
    func emailFormatNotChecked() throws {
        _ = try LoginCredentials(email: "not-an-email", password: "secret")
    }

    @Test("errors speak pt-BR")
    func messages() {
        #expect(CredentialsError.emptyFields.message == "Preencha todos os campos")
    }
}

@Suite("Registration")
struct RegistrationTests {

    @Test("a valid registration is accepted")
    func valid() throws {
        let registration = try Registration(name: "Ana", email: "a@b.com", password: "12345678")

        #expect(registration.name == "Ana")
        #expect(registration.email == "a@b.com")
        #expect(registration.password == "12345678")
    }

    @Test("a blank name, email or password is rejected as empty fields", arguments: [
        ("", "a@b.com", "12345678"), ("  ", "a@b.com", "12345678"),
        ("Ana", "", "12345678"), ("Ana", "a@b.com", "   "),
    ])
    func blankFields(name: String, email: String, password: String) {
        #expect(throws: CredentialsError.emptyFields) {
            try Registration(name: name, email: email, password: password)
        }
    }

    @Test("a password under the minimum is too short, exactly the minimum is fine")
    func passwordLength() throws {
        let minimum = Registration.minimumPasswordLength
        let short = String(repeating: "x", count: minimum - 1)
        let exact = String(repeating: "x", count: minimum)

        #expect(throws: CredentialsError.passwordTooShort(minimum: minimum)) {
            try Registration(name: "Ana", email: "a@b.com", password: short)
        }
        #expect(throws: Never.self) {
            try Registration(name: "Ana", email: "a@b.com", password: exact)
        }
    }

    @Test("the minimum is 8 and empty fields win over a short password")
    func minimumAndPrecedence() {
        #expect(Registration.minimumPasswordLength == 8)
        #expect(throws: CredentialsError.emptyFields) {
            try Registration(name: "", email: "a@b.com", password: "123")
        }
    }

    @Test("the too-short message states the minimum in pt-BR")
    func tooShortMessage() {
        #expect(CredentialsError.passwordTooShort(minimum: 8).message == "A senha deve ter pelo menos 8 caracteres")
    }
}
