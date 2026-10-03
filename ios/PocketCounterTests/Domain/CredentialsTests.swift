import Testing

@testable import PocketCounter

@Suite("Credentials")
struct CredentialsTests {

    @Test("valid login credentials are accepted")
    func validLogin() throws {
        let credentials = try Credentials(email: "a@b.com", password: "12345678")

        #expect(credentials.email == "a@b.com")
        #expect(credentials.password == "12345678")
        #expect(credentials.name == nil)
    }

    @Test("a blank email or password is rejected as empty fields", arguments: [
        ("", "12345678"), ("   ", "12345678"), ("a@b.com", ""), ("a@b.com", "   "),
    ])
    func blankFields(email: String, password: String) {
        #expect(throws: CredentialsError.emptyFields) {
            try Credentials(email: email, password: password)
        }
    }

    @Test("a password under 8 characters is too short, exactly 8 is fine")
    func passwordLength() throws {
        #expect(throws: CredentialsError.passwordTooShort(minimum: 8)) {
            try Credentials(email: "a@b.com", password: "1234567")
        }
        #expect(throws: Never.self) {
            try Credentials(email: "a@b.com", password: "12345678")
        }
    }

    @Test("empty fields win over a short password")
    func emptyBeforeShort() {
        #expect(throws: CredentialsError.emptyFields) {
            try Credentials(email: "", password: "123")
        }
    }

    @Test("registration additionally requires a name")
    func registerNeedsName() throws {
        #expect(throws: CredentialsError.emptyFields) {
            try Credentials(name: "  ", email: "a@b.com", password: "12345678")
        }
        let credentials = try Credentials(name: "Ana", email: "a@b.com", password: "12345678")
        #expect(credentials.name == "Ana")
    }

    @Test("errors speak pt-BR")
    func messages() {
        #expect(CredentialsError.emptyFields.message == "Preencha todos os campos")
        #expect(CredentialsError.passwordTooShort(minimum: 8).message == "A senha deve ter pelo menos 8 caracteres")
    }
}
