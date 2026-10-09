import Foundation
import Testing

@testable import PocketCounter

@Suite("ReadingFailure translation")
struct ReadingFailureTranslationTests {
    private func status(_ code: Int, details: [String] = [], message: String = "") -> APIError {
        .status(code: code, server: ServerMessage(code: "X", message: message, details: details, correlationId: nil))
    }

    @Test("the two authentication outcomes stay separate")
    func authentication() {
        #expect(ReadingFailure(APIError.sessionExpired) == .sessionExpired)
        #expect(ReadingFailure(APIError.authenticationUnavailable) == .authenticationUnavailable)
        #expect(ReadingFailure(APIError.status(code: 401, server: nil)) == .sessionExpired)
    }

    @Test("a 429 is its own case, not a server failure: the user can wait it out")
    func tooManyRequests() {
        #expect(ReadingFailure(APIError.status(code: 429, server: nil)) == .tooManyRequests)
    }

    @Test("a transport failure and a cancellation are both unreachable")
    func unreachable() {
        #expect(ReadingFailure(APIError.transport(URLError(.notConnectedToInternet))) == .unreachable)
        #expect(ReadingFailure(APIError.cancelled) == .unreachable)
    }

    @Test("a 400 or 422 prefers details[0], then the message, then a fallback", arguments: [400, 422])
    func validation(code: Int) {
        #expect(ReadingFailure(status(code, details: ["O texto é longo demais"], message: "Erro")) == .rejected("O texto é longo demais"))
        #expect(ReadingFailure(status(code, message: "Erro")) == .rejected("Erro"))
        #expect(ReadingFailure(APIError.status(code: code, server: nil)) == .rejected("Não foi possível processar a solicitação"))
    }

    @Test("a 403 is rejected and does not expire the session")
    func forbidden() {
        #expect(ReadingFailure(status(403, message: "Sem acesso")) == .rejected("Sem acesso"))
    }

    @Test("every other status, a decoding failure and an invalid request are server failures", arguments: [
        APIError.status(code: 500, server: nil),
        .status(code: 404, server: nil),
        .status(code: 413, server: nil),
        .status(code: 502, server: nil),
        .decoding(endpoint: "/x", underlying: "bad"),
        .invalidRequest("bad"),
    ])
    func server(error: APIError) {
        #expect(ReadingFailure(error) == .server)
    }

    @Test("a mapping failure is a server failure")
    func mapping() {
        #expect(ReadingFailure(MappingFailure.unknownEnum(entity: "SentenceReading", field: "missing", value: "TAG")) == .server)
    }
}
