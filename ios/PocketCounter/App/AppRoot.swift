import SwiftUI

/// Root of the app. Becomes the session gate — splash / login / tab shell — once the
/// session exists.
struct AppRoot: View {
    var body: some View {
        PocketColor.background.ignoresSafeArea()
    }
}
