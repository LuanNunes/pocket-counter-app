import Foundation
import Testing

@testable import PocketCounter

@Suite("JWTPayload")
struct JWTPayloadTests {

    private func token(payload: String) -> String {
        let base64url = Data(payload.utf8).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        return "eyJhbGciOiJIUzI1NiJ9.\(base64url).c2ln"
    }

    @Test("a payload whose base64url length is not a multiple of 4 decodes", arguments: [1, 2])
    func unpadded(remainder: Int) throws {
        // Trailing spaces are valid JSON and steer the byte count (n % 3) the padding depends on.
        var json = #"{"sub":"1","email":"a@b.co"}"#
        while json.utf8.count % 3 != remainder { json += " " }
        let jwt = token(payload: json)
        let segment = try #require(jwt.split(separator: ".").dropFirst().first)
        #expect(segment.count % 4 != 0)

        let payload = try #require(JWTPayload(accessToken: jwt))

        #expect(payload.email == "a@b.co")
    }

    @Test("a payload using the url-safe alphabet decodes")
    func urlSafeAlphabet() throws {
        // "?>" encodes to a group ending in "/", which base64url writes as "_".
        let json = #"{"name":"?>?>?>?>~~","email":"a@b.co"}"#
        let jwt = token(payload: json)
        let segment = try #require(jwt.split(separator: ".").dropFirst().first)
        #expect(segment.contains("-") || segment.contains("_"))

        let payload = try #require(JWTPayload(accessToken: jwt))

        #expect(payload.name == "?>?>?>?>~~")
    }

    @Test("a token without exactly three segments is rejected", arguments: ["a.b", "a.b.c.d", "abc", ""])
    func wrongSegmentCount(raw: String) {
        #expect(JWTPayload(accessToken: raw) == nil)
    }

    @Test("a payload that is not JSON is rejected")
    func notJSON() {
        #expect(JWTPayload(accessToken: "a.!!!.c") == nil)
    }

    @Test("missing claims stay nil")
    func optionalClaims() throws {
        let payload = try #require(JWTPayload(accessToken: token(payload: #"{"sub":"u1"}"#)))

        #expect(payload.sub == "u1")
        #expect(payload.email == nil)
        #expect(payload.name == nil)
    }

    @Test("the user is named by the claim")
    func userName() throws {
        let payload = try #require(JWTPayload(accessToken: token(payload: #"{"name":"Ana","email":"a@b.co"}"#)))

        #expect(payload.user == AuthenticatedUser(name: "Ana", email: "a@b.co"))
        #expect(payload.user?.displayName == "Ana")
    }

    @Test("an empty name falls back to the email", arguments: ["", "   "])
    func emptyNameFallsBack(name: String) throws {
        let payload = try #require(JWTPayload(accessToken: token(payload: #"{"name":"\#(name)","email":"a@b.co"}"#)))

        #expect(payload.user?.displayName == "a@b.co")
        #expect(payload.user?.email == "a@b.co")
    }

    @Test("a payload without an email yields no user")
    func noEmailNoUser() throws {
        let payload = try #require(JWTPayload(accessToken: token(payload: #"{"name":"Ana"}"#)))

        #expect(payload.user == nil)
    }
}
