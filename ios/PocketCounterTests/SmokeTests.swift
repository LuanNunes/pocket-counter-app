import Testing

@testable import PocketCounter

/// Proves the test target compiles against the app module and that Swift Testing runs.
/// Real coverage starts with the domain models.
@Suite("Smoke")
struct SmokeTests {

    @Test("the environment name parses from a bundle id suffix")
    func environmentNameCases() {
        #expect(AppEnvironment.Name(rawValue: "local") == .local)
        #expect(AppEnvironment.Name(rawValue: "dev") == .dev)
        #expect(AppEnvironment.Name(rawValue: "prod") == .prod)
        #expect(AppEnvironment.Name(rawValue: "staging") == nil)
    }
}
