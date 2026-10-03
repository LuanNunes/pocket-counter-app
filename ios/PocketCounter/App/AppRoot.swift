import SwiftUI

/// Root of the app.
///
/// Placeholder: it currently proves the build and the per-environment configuration
/// wiring. It becomes the session gate — splash / login / tab shell — once
/// `SessionStore` exists.
struct AppRoot: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("PocketCounter")
                .font(.system(.largeTitle, weight: .bold))

            Text(AppEnvironment.name.rawValue)
                .font(.subheadline)
                .foregroundStyle(.secondary)

            Text(AppEnvironment.baseURL.absoluteString)
                .font(.footnote.monospaced())
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemGroupedBackground))
    }
}

#Preview {
    AppRoot()
}
