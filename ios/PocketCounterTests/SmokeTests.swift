import Testing

@testable import PocketCounter

/// Proves the test target compiles against the app module and that Swift Testing runs.
/// Real coverage starts with the domain models.
@Suite("Smoke")
struct SmokeTests {

    @Test("the test target links against the app module")
    func links() {
        #expect(AppConfiguration.Environment(rawValue: "dev") == .dev)
    }
}
