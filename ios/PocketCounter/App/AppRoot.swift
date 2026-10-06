import SwiftUI

/// The session gate. The `switch` is exhaustive with no `default:` on purpose — a new gate case
/// must break the build rather than fall through to something plausible.
struct AppRoot: View {
    let session: SessionModel
    let container: AppContainer

    @Environment(\.scenePhase) private var scenePhase

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            switch session.state.gate {
            case .resolving:
                LaunchSplash(phase: .restoring)

            // One branch, so Login keeps one structural identity.
            case .signedOut,
                 .undetermined where session.state.prefersPassword:
                login

            case .undetermined:
                LaunchSplash(
                    phase: .unreadable(
                        offersEscape: session.state.offersEscape,
                        isRetrying: session.state.isResolving
                    ),
                    onRetry: { Task { await retry() } },
                    onUsePassword: { session.preferPassword() }
                )

            case .signedIn(let user):
                AppShell(
                    container: container,
                    user: user,
                    signOutFailed: session.state.signOutFailed,
                    onSessionExpired: { await session.sessionEnded() },
                    onSignOut: { Task { await session.signOut() } }
                )
                .id(user.id)
            }
        }
        .animation(gateAnimation, value: session.state.gate)
        .task { await session.resolve() }
        .onChange(of: scenePhase) { _, phase in
            // A read before first unlock resolves itself on foreground. Not under the escape
            // form: success there moves the gate into the account they may be leaving.
            guard phase == .active, session.state.gate == .undetermined, !session.state.prefersPassword else { return }
            Task { await session.resolve() }
        }
    }

    private var gateAnimation: Animation {
        reduceMotion ? PocketMotion.reduced : PocketMotion.standard
    }

    private var login: some View {
        AuthFlow(
            signIn: session.signIn,
            register: session.register,
            onBack: session.state.prefersPassword ? { session.cancelPasswordEscape(); Task { await retry() } } : nil
        )
    }

    private func retry() async {
        await session.resolve()
        guard session.state.gate == .undetermined else { return }
        AccessibilityNotification.Announcement("Ainda não foi possível abrir sua sessão").post()
    }
}
