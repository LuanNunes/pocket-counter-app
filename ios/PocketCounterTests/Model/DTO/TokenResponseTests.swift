import Foundation
import Testing

@testable import PocketCounter

@Suite("TokenResponse")
struct TokenResponseTests {

    private func decode(_ json: String) throws -> TokenResponse {
        try JSONDecoder().decode(TokenResponse.self, from: Data(json.utf8))
    }

    @Test("the backend's full response decodes")
    func full() throws {
        let json = #"""
        {"accessToken":"a","refreshToken":"r","expiresIn":3600,"tokenType":"Bearer","tutorialCompleted":true}
        """#

        let response = try decode(json)

        #expect(response.accessToken == "a")
        #expect(response.refreshToken == "r")
        #expect(response.expiresIn == 3600)
        #expect(response.tokenType == "Bearer")
        #expect(response.tutorialCompleted == true)
    }

    @Test("a response carrying only the tokens decodes, with the rest nil")
    func tokensOnly() throws {
        let response = try decode(#"{"accessToken":"a","refreshToken":"r"}"#)

        #expect(response.expiresIn == nil)
        #expect(response.tokenType == nil)
        #expect(response.tutorialCompleted == nil)
    }

    @Test("a response missing a token does not decode", arguments: [
        #"{"refreshToken":"r"}"#, #"{"accessToken":"a"}"#, "{}",
    ])
    func missingToken(json: String) {
        #expect(throws: DecodingError.self) { try decode(json) }
    }
}
