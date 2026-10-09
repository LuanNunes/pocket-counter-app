import Testing

@testable import PocketCounter

@Suite("WriteFailureMessage")
struct WriteFailureMessageTests {
    private static let title = "Não foi possível salvar"
    private static let failures: [WriteFailure] = [
        .unreachable, .server, .authenticationUnavailable, .vanished, .rejected("Sem permissão"),
    ]

    @Test("a session that ended renders nothing: the session gate shows the consequence", arguments: [
        WriteFailureMessage.Subject.saving, .deleting,
    ])
    func silent(subject: WriteFailureMessage.Subject) {
        #expect(WriteFailureMessage.message(for: .sessionExpired, subject: subject) == nil)
    }

    @Test("every other failure says what happened", arguments: [
        (WriteFailure.unreachable, PocketNotice(kind: .offline, title: title, detail: "Sem conexão com o servidor.")),
        (.server, PocketNotice(kind: .error, title: title, detail: "Tente de novo em instantes.")),
        (.authenticationUnavailable, PocketNotice(
            kind: .info, title: title, detail: "Isso costuma ser temporário. Seus dados continuam salvos.")),
        (.vanished, PocketNotice(
            kind: .error, title: "Este lançamento não existe mais", detail: "Pode ter sido excluído em outro aparelho.")),
        (.rejected("Sem permissão"), PocketNotice(kind: .error, title: title, detail: "Sem permissão")),
        (.duplicate("Já existe Consulta do cachorro"), PocketNotice(
            kind: .error, title: title, detail: "Já existe Consulta do cachorro")),
    ])
    func shown(failure: WriteFailure, expected: PocketNotice) {
        #expect(WriteFailureMessage.message(for: failure, subject: .saving) == expected)
    }

    @Test("a rejection filters the server's text and falls back when it is not presentable", arguments: [
        "", "   ", "Validation failed", "error.tx.invalid",
    ])
    func rejectedFallback(text: String) {
        #expect(WriteFailureMessage.message(for: .rejected(text), subject: .saving)?.detail == "O servidor recusou a alteração.")
    }

    @Test("the title names the subject")
    func titles() {
        let unreachable = WriteFailure.unreachable
        #expect(WriteFailureMessage.message(for: unreachable, subject: .saving)?.title == "Não foi possível salvar")
        #expect(WriteFailureMessage.message(for: unreachable, subject: .deleting)?.title == "Não foi possível excluir")
        #expect(WriteFailureMessage.message(for: unreachable, subject: .reordering)?.title == "Não foi possível reordenar")
    }

    @Test("a reorder that finds a row gone talks about the list, not the row")
    func reorderVanished() {
        #expect(WriteFailureMessage.message(for: .vanished, subject: .reordering) == PocketNotice(
            kind: .error, title: "A lista mudou",
            detail: "Algum lançamento foi excluído em outro aparelho."))
    }

    @Test("a reorder says nothing when the session ended")
    func reorderSilent() {
        #expect(WriteFailureMessage.message(for: .sessionExpired, subject: .reordering) == nil)
    }

    @Test("a vanished row keeps its own title whatever the subject", arguments: [
        WriteFailureMessage.Subject.saving, .deleting,
    ])
    func vanishedTitle(subject: WriteFailureMessage.Subject) {
        #expect(WriteFailureMessage.message(for: .vanished, subject: subject)?.title == "Este lançamento não existe mais")
    }

    @Test("the detail and kind never depend on the subject: only the title does", arguments: failures)
    func detailIsSubjectFree(failure: WriteFailure) {
        let notices = [WriteFailureMessage.Subject.saving, .deleting].compactMap {
            WriteFailureMessage.message(for: failure, subject: $0)
        }

        #expect(notices.count == 2)
        #expect(notices.allSatisfy { $0.detail == notices[0].detail && $0.kind == notices[0].kind })
    }
}
