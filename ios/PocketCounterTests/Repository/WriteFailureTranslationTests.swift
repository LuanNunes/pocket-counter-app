import Foundation
import OSLog
import Testing

@testable import PocketCounter

@Suite("WriteFailure translation")
struct WriteFailureTranslationTests {
    private func status(_ code: Int, details: [String] = [], message: String = "") -> APIError {
        .status(code: code, server: ServerMessage(code: "X", message: message, details: details, correlationId: nil))
    }

    @Test("the two authentication outcomes stay separate")
    func authentication() {
        #expect(WriteFailure(APIError.sessionExpired) == .sessionExpired)
        #expect(WriteFailure(APIError.authenticationUnavailable) == .authenticationUnavailable)
    }

    @Test("a 401 after the retry means the session expired")
    func unauthorized() {
        #expect(WriteFailure(APIError.status(code: 401, server: nil)) == .sessionExpired)
    }

    @Test("a transport failure and a cancellation are both unreachable")
    func unreachable() {
        #expect(WriteFailure(APIError.transport(URLError(.notConnectedToInternet))) == .unreachable)
        #expect(WriteFailure(APIError.cancelled) == .unreachable)
    }

    @Test("a 404 on a write means the row is gone")
    func vanished() {
        #expect(WriteFailure(APIError.status(code: 404, server: nil)) == .vanished)
    }

    @Test("a 403 is rejected with the server's message and does not expire the session")
    func forbidden() {
        #expect(WriteFailure(status(403, message: "Sem acesso")) == .rejected("Sem acesso"))
        #expect(WriteFailure(APIError.status(code: 403, server: nil)) == .rejected("Você não tem acesso a este recurso"))
    }

    @Test("a 400 or 422 prefers details[0], then the message, then a fallback", arguments: [400, 422])
    func validation(code: Int) {
        #expect(WriteFailure(status(code, details: ["campo inválido", "outro"], message: "Erro")) == .rejected("campo inválido"))
        #expect(WriteFailure(status(code, message: "Erro")) == .rejected("Erro"))
        #expect(WriteFailure(APIError.status(code: code, server: nil)) == .rejected("Não foi possível processar a solicitação"))
    }

    @Test("every other status, a decoding failure and an invalid request are server failures", arguments: [
        APIError.status(code: 500, server: nil),
        .status(code: 502, server: nil),
        .decoding(endpoint: "/x", underlying: "bad"),
        .invalidRequest("bad"),
    ])
    func server(error: APIError) {
        #expect(WriteFailure(error) == .server)
    }

    @Test("a 409 is the server naming an existing row, not a server failure")
    func duplicate() {
        let sentence = "Já existe Consulta do cachorro em 07/10/2026 no valor de 250.00"

        #expect(
            WriteFailure(status(409, details: ["7f3a1c2e-0000-4000-8000-000000000001"], message: sentence))
                == .duplicate(sentence)
        )
    }

    @Test("a 409 with no body still carries a sentence")
    func duplicateWithoutBody() {
        #expect(
            WriteFailure(APIError.status(code: 409, server: nil))
                == .duplicate("Já existe um lançamento igual neste mês")
        )
    }

    @Test("a 409's details hold the existing row's id, which is never shown")
    func duplicateIgnoresDetails() {
        #expect(
            WriteFailure(status(409, details: ["7f3a1c2e-0000-4000-8000-000000000001"]))
                == .duplicate("Já existe um lançamento igual neste mês")
        )
    }

    @Test("a decoding failure logs the endpoint it came from")
    func decodingIsLogged() throws {
        let marker = "/path/\(UUID().uuidString.prefix(8))"

        _ = WriteFailure(APIError.decoding(endpoint: marker, underlying: "bad"))

        let store = try OSLogStore(scope: .currentProcessIdentifier)
        let messages = try store.getEntries(at: store.position(date: Date(timeIntervalSinceNow: -60)))
            .compactMap { $0 as? OSLogEntryLog }
            .filter { $0.subsystem == "com.resolveprogramming.pocketcounter" }
            .map(\.composedMessage)
            .filter { $0.contains(marker) }
        #expect(messages.count == 1)
    }
}
