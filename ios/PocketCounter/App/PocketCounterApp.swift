import SwiftUI

@main
struct PocketCounterApp: App {
    /// On the `App`, not on `AppRoot`: the `App` value is created once per process, so the
    /// session model is initialised exactly once.
    @State private var session: SessionModel

    init() {
        let configuration: AppConfiguration
        do {
            configuration = try AppConfiguration.mainBundle()
        } catch {
            fatalError("Invalid build configuration: \(error) — check Config/*.xcconfig and Info.plist")
        }
        let container = AppContainer(configuration: configuration)
        _session = State(initialValue: SessionModel(repository: container.sessionRepository))
    }

    var body: some Scene {
        WindowGroup {
            AppRoot(session: session)
        }
    }
}
