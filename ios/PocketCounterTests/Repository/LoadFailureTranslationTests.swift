import Foundation
import OSLog
import Testing

@testable import PocketCounter

@Suite("LoadFailure translation")
struct LoadFailureTranslationTests {
    private func status(_ code: Int, details: [String] = [], message: String = "") -> APIError {
        .status(code: code, server: ServerMessage(code: "X", message: message, details: details, correlationId: nil))
    }

    @Test("the two authentication outcomes stay separate")
    func authentication() {
        #expect(LoadFailure(APIError.sessionExpired) == .sessionExpired)
        #expect(LoadFailure(APIError.authenticationUnavailable) == .authenticationUnavailable)
    }

    @Test("cancellation is abandoned and a transport failure is unreachable")
    func transport() {
        #expect(LoadFailure(APIError.cancelled) == .abandoned)
        #expect(LoadFailure(APIError.transport(URLError(.notConnectedToInternet))) == .unreachable)
    }

    @Test("a 401 after the retry means the session expired")
    func unauthorized() {
        #expect(LoadFailure(APIError.status(code: 401, server: nil)) == .sessionExpired)
    }

    @Test("a 403 is rejected with the server's message and does not expire the session")
    func forbidden() {
        #expect(LoadFailure(status(403, message: "Sem acesso")) == .rejected("Sem acesso"))
        #expect(LoadFailure(APIError.status(code: 403, server: nil)) == .rejected("Você não tem acesso a este recurso"))
    }

    @Test("a 404 is not found")
    func missing() {
        #expect(LoadFailure(APIError.status(code: 404, server: nil)) == .notFound)
    }

    @Test("a 400 or 422 prefers details[0], then the message, then a fallback", arguments: [400, 422])
    func validation(code: Int) {
        #expect(LoadFailure(status(code, details: ["campo inválido", "outro"], message: "Erro")) == .rejected("campo inválido"))
        #expect(LoadFailure(status(code, message: "Erro")) == .rejected("Erro"))
        #expect(LoadFailure(APIError.status(code: code, server: nil)) == .rejected("Não foi possível processar a solicitação"))
    }

    @Test("every other status, a decoding failure and an invalid request are server failures", arguments: [
        APIError.status(code: 500, server: nil),
        .status(code: 409, server: nil),
        .status(code: 502, server: nil),
        .decoding(endpoint: "/x", underlying: "bad"),
        .invalidRequest("bad"),
    ])
    func server(error: APIError) {
        #expect(LoadFailure(error) == .server)
    }

    @Test("a mapping failure is a server failure")
    func mapping() {
        #expect(LoadFailure(MappingFailure.invalidRef(1)) == .server)
    }

    private func logged(containing marker: String) throws -> [String] {
        let store = try OSLogStore(scope: .currentProcessIdentifier)
        return try store.getEntries(at: store.position(date: Date(timeIntervalSinceNow: -60)))
            .compactMap { $0 as? OSLogEntryLog }
            .filter { $0.subsystem == "com.resolveprogramming.pocketcounter" }
            .map(\.composedMessage)
            .filter { $0.contains(marker) }
    }

    @Test("a mapping failure logs its entity and field and never the value")
    func mappingFailureIsLogged() throws {
        let marker = "Entity\(UUID().uuidString.prefix(8))"

        _ = LoadFailure(MappingFailure.unknownEnum(entity: marker, field: "kind", value: "user secret text"))

        let messages = try logged(containing: marker)
        #expect(messages.count == 1)
        #expect(messages.first?.contains("kind") == true)
        #expect(messages.first?.contains("user secret text") == false)
    }

    @Test("a decoding failure logs the endpoint it came from")
    func decodingIsLogged() throws {
        let marker = "/path/\(UUID().uuidString.prefix(8))"

        _ = LoadFailure(APIError.decoding(endpoint: marker, underlying: "bad"))

        #expect(try logged(containing: marker).count == 1)
    }
}
