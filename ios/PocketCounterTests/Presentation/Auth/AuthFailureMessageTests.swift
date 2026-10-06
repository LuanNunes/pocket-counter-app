import Testing

@testable import PocketCounter

@Suite("AuthFailureMessage")
struct AuthFailureMessageTests {

    private static let generic = "Não foi possível concluir. Revise os dados e tente novamente"
    private static let google = "Não foi possível entrar com o Google"

    private func text(_ failure: AuthenticationFailure, _ path: AuthPath) -> AuthMessage? {
        AuthFailureMessage.text(for: failure, path: path)
    }

    @Test("an abandoned request renders nothing on every path", arguments: [AuthPath.password, .google, .register])
    func abandoned(path: AuthPath) {
        #expect(text(.abandoned, path) == nil)
    }

    @Test("being offline is its own severity on every path", arguments: [AuthPath.password, .google, .register])
    func unreachable(path: AuthPath) {
        #expect(text(.unreachable, path) == AuthMessage(kind: .offline, text: "Sem conexão com o servidor"))
    }

    @Test("password and register copy, per failure", arguments: [AuthPath.password, .register])
    func passwordCopy(path: AuthPath) {
        #expect(text(.invalidCredentials, path)?.text == "E-mail ou senha incorretos")
        #expect(text(.server, path) == AuthMessage(kind: .error, text: "Erro inesperado"))
    }

    @Test("a duplicate e-mail on register offers to sign in to that account")
    func emailAlreadyRegistered() {
        #expect(text(.emailAlreadyRegistered, .register)
            == AuthMessage(kind: .error, text: "Este e-mail já está cadastrado", action: .signInWithThisAccount))
    }

    @Test("the same 401 reads differently on the Google path: no password was typed")
    func sameFailureDiffersByPath() {
        #expect(text(.invalidCredentials, .password)?.text == "E-mail ou senha incorretos")
        #expect(text(.invalidCredentials, .google) == AuthMessage(kind: .error, text: Self.google))
    }

    @Test("every Google failure collapses into one message, whatever the server said", arguments: [
        AuthenticationFailure.invalidCredentials, .emailAlreadyRegistered, .server, .rejected("Token audience mismatch"),
    ])
    func googleCollapses(failure: AuthenticationFailure) {
        #expect(text(failure, .google) == AuthMessage(kind: .error, text: Self.google))
    }

    @Test("a presentable server text is shown verbatim", arguments: [AuthPath.password, .register])
    func verbatim(path: AuthPath) {
        let copy = "A senha precisa ter ao menos 8 caracteres"

        #expect(text(.rejected(copy), path) == AuthMessage(kind: .error, text: copy))
    }

    @Test("a rejected text that is not presentable falls back to the generic copy", arguments: [
        "", "   \n", "validation.user.email_invalid", "abcd.efgh",
        "Domain exception occurred", "Validation failed", "An unexpected error occurred", "Invalid request parameter",
    ])
    func generic(payload: String) {
        #expect(text(.rejected(payload), .password) == AuthMessage(kind: .error, text: Self.generic))
    }

    @Test("what is not a bundle key is not mistaken for one", arguments: [
        "Senha inválida.", "abcd.efg", "Informe um e-mail válido. Tente de novo.",
    ])
    func notBundleKeys(payload: String) {
        #expect(text(.rejected(payload), .password)?.text == payload)
    }

    @Test("a presentable text is trimmed of surrounding whitespace only")
    func trimmed() {
        #expect(text(.rejected("  E-mail inválido \n"), .password)?.text == "E-mail inválido")
    }
}
