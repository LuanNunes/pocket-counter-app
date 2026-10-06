import Testing

@testable import PocketCounter

@Suite("LoadFailureMessage")
struct LoadFailureMessageTests {

    private static let silent: [LoadFailure] = [.sessionExpired, .abandoned]
    private static let shown: [LoadFailure] = [
        .authenticationUnavailable, .unreachable, .notFound, .server,
    ]
    private static let fallback = "Não foi possível carregar"
    private static let stillShowing = "Mostrando os dados anteriores."

    @Test("a session that ended and a cancelled load render nothing", arguments: silent)
    func silentFailures(failure: LoadFailure) {
        #expect(LoadFailureMessage.blocking(for: failure) == nil)
        #expect(LoadFailureMessage.notice(for: failure) == nil)
    }

    @Test("a blocking failure says what happened and what to do", arguments: [
        (LoadFailure.unreachable, PocketNotice(
            kind: .offline, title: "Sem conexão com o servidor", detail: "Verifique sua internet e tente de novo.")),
        (.server, PocketNotice(
            kind: .error, title: "Algo deu errado",
            detail: "O servidor não conseguiu responder. Tente de novo em instantes.")),
        (.authenticationUnavailable, PocketNotice(
            kind: .info, title: "Não conseguimos confirmar sua sessão",
            detail: "Isso costuma ser temporário. Seus dados continuam salvos.")),
        (.notFound, PocketNotice(
            kind: .error, title: "Não encontramos esses dados", detail: "Eles podem ter sido removidos.")),
    ])
    func blocking(failure: LoadFailure, expected: PocketNotice) {
        #expect(LoadFailureMessage.blocking(for: failure) == expected)
    }

    @Test("a notice over data keeps the title and says the data shown is the previous one", arguments: shown)
    func notice(failure: LoadFailure) throws {
        let blocking = try #require(LoadFailureMessage.blocking(for: failure))

        let notice = LoadFailureMessage.notice(for: failure)

        #expect(notice == PocketNotice(kind: blocking.kind, title: blocking.title, detail: Self.stillShowing))
    }

    @Test("a presentable server text is shown verbatim")
    func rejectedVerbatim() {
        let expected = PocketNotice(kind: .error, title: "Mês inválido", detail: nil)

        #expect(LoadFailureMessage.blocking(for: .rejected("Mês inválido")) == expected)
        #expect(LoadFailureMessage.notice(for: .rejected("Mês inválido"))?.title == "Mês inválido")
    }

    @Test("a rejected text that is not presentable falls back", arguments: [
        "", "   \n", "validation.month.invalid", "Domain exception occurred", "Validation failed",
    ])
    func rejectedFallback(payload: String) {
        let blocking = LoadFailureMessage.blocking(for: .rejected(payload))
        let notice = LoadFailureMessage.notice(for: .rejected(payload))

        #expect(blocking == PocketNotice(kind: .error, title: Self.fallback, detail: nil))
        #expect(notice == PocketNotice(kind: .error, title: Self.fallback, detail: Self.stillShowing))
    }

    @Test("no failed lookup renders nothing")
    func degradedNone() {
        #expect(LoadFailureMessage.degraded([]) == nil)
    }

    @Test("failed lookups are named in a fixed order, whatever the set holds", arguments: [
        ([LookupKind.categories], "As categorias não carregaram"),
        ([.tags], "As tags não carregaram"),
        ([.cards], "Os cartões não carregaram"),
        ([.categories, .tags], "As categorias e as tags não carregaram"),
        ([.categories, .cards], "As categorias e os cartões não carregaram"),
        ([.tags, .cards], "As tags e os cartões não carregaram"),
        ([.cards, .tags, .categories], "As categorias, as tags e os cartões não carregaram"),
    ])
    func degraded(kinds: [LookupKind], title: String) {
        let notice = LoadFailureMessage.degraded(Set(kinds))

        #expect(notice == PocketNotice(
            kind: .warning, title: title, detail: "Os valores estão certos; só os nomes estão faltando."))
    }
}
