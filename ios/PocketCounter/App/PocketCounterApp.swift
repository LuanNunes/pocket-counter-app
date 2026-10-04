import SwiftUI

@main
struct PocketCounterApp: App {
    private let configuration: AppConfiguration

    init() {
        do {
            configuration = try AppConfiguration.mainBundle()
        } catch {
            fatalError("Invalid build configuration: \(error) — check Config/*.xcconfig and Info.plist")
        }
    }

    var body: some Scene {
        WindowGroup {
            AppRoot()
        }
    }
}
