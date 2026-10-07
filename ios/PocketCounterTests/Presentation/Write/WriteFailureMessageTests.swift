import Testing

@testable import PocketCounter

@Suite("WriteFailureMessage")
struct WriteFailureMessageTests {
    private static let title = "Não foi possível salvar"

    @Test("a session that ended renders nothing: the session gate shows the consequence")
    func silent() {
        #expect(WriteFailureMessage.message(for: .sessionExpired) == nil)
    }

    @Test("every other failure says what happened", arguments: [
        (WriteFailure.unreachable, PocketNotice(kind: .offline, title: title, detail: "Sem conexão com o servidor.")),
        (.server, PocketNotice(kind: .error, title: title, detail: "Tente de novo em instantes.")),
        (.authenticationUnavailable, PocketNotice(
            kind: .info, title: title, detail: "Isso costuma ser temporário. Seus dados continuam salvos.")),
        (.vanished, PocketNotice(
            kind: .error, title: "Este lançamento não existe mais", detail: "Pode ter sido excluído em outro aparelho.")),
        (.rejected("Sem permissão"), PocketNotice(kind: .error, title: title, detail: "Sem permissão")),
    ])
    func shown(failure: WriteFailure, expected: PocketNotice) {
        #expect(WriteFailureMessage.message(for: failure) == expected)
    }

    @Test("a rejection filters the server's text and falls back when it is not presentable", arguments: [
        "", "   ", "Validation failed", "error.tx.invalid",
    ])
    func rejectedFallback(text: String) {
        #expect(WriteFailureMessage.message(for: .rejected(text))?.detail == "O servidor recusou a alteração.")
    }
}
