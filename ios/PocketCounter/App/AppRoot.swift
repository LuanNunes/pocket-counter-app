import SwiftUI

/// The session gate. The `switch` is exhaustive with no `default:` on purpose — a new gate case
/// must break the build rather than fall through to something plausible.
struct AppRoot: View {
    let session: SessionModel

    @Environment(\.scenePhase) private var scenePhase

    /// The escape offered after two failed retries. Local to the gate: it changes what is shown,
    /// never the stored session.
    @State private var prefersPassword = false

    var body: some View {
        ZStack {
            switch session.state.gate {
            case .resolving:
                LaunchSplash(phase: .restoring)

            case .undetermined where prefersPassword:
                login

            case .undetermined:
                LaunchSplash(
                    phase: .unreadable(
                        attempts: session.state.failedResolveAttempts,
                        isRetrying: session.state.isResolving
                    ),
                    onRetry: { Task { await retry() } },
                    onUsePassword: { prefersPassword = true }
                )

            case .signedOut:
                login

            case .signedIn(let user):
                AppShellPlaceholder(
                    user: user,
                    signOutFailed: session.state.signOutFailed
                ) {
                    Task { await session.signOut() }
                }
            }
        }
        .animation(.timingCurve(0.32, 0.72, 0, 1, duration: 0.42), value: session.state.gate)
        .task { await session.resolve() }
        .onChange(of: scenePhase) { _, phase in
            // The common cause of `.undetermined` is a read before first unlock; by the time the
            // user foregrounds the app it resolves on its own.
            guard phase == .active, session.state.gate == .undetermined else { return }
            Task { await session.resolve() }
        }
    }

    private var login: some View {
        AuthFlow(signIn: session.signIn, register: session.register)
    }

    private func retry() async {
        await session.resolve()
        guard session.state.gate == .undetermined else { return }
        AccessibilityNotification.Announcement("Ainda não foi possível abrir sua sessão").post()
    }
}
