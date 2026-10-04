import Testing

@testable import PocketCounter

@Suite("UserID")
struct UserIDTests {

    @Test("a UUID keeps the exact string it was given, not a re-rendering of it", arguments: [
        "7b1f0f1e-8b9c-4c2a-9a1d-3f5e6c7d8a90",
        "7B1F0F1E-8B9C-4C2A-9A1D-3F5E6C7D8A90",
    ])
    func keepsWireForm(raw: String) {
        #expect(UserID(rawValue: raw)?.rawValue == raw)
    }

    @Test("a string that is not a UUID is not a user id", arguments: ["", "42", "not-a-uuid", " 7b1f0f1e-8b9c-4c2a-9a1d-3f5e6c7d8a90"])
    func rejectsOthers(raw: String) {
        #expect(UserID(rawValue: raw) == nil)
    }
}
