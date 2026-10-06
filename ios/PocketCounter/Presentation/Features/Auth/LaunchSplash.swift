import SwiftUI

/// Launch and "could not read the session". Same composition either way, so one becoming the
/// other does not recompose jarringly; `.unreadable` is distinct **by addition**.
struct LaunchSplash: View {
    enum Phase: Equatable {
        case restoring
        /// `offersEscape` is `SessionState.offersEscape`; the threshold is not repeated here.
        case unreadable(offersEscape: Bool, isRetrying: Bool)
    }

    let phase: Phase
    var onRetry: () -> Void = {}
    var onUsePassword: () -> Void = {}

    /// A Keychain read is usually sub-millisecond; a spinner that flashes is worse than none.
    @State private var showsSpinner = false

    var body: some View {
        ZStack {
            PocketColor.heroSurface.ignoresSafeArea()

            VStack(spacing: 24) {
                AuthBrandMark(.hero)

                switch phase {
                case .restoring:
                    if showsSpinner {
                        ProgressView()
                            .tint(PocketColor.onHero)
                            .transition(.opacity)
                    }
                case .unreadable(let offersEscape, let isRetrying):
                    unreadable(offersEscape: offersEscape, isRetrying: isRetrying)
                }
            }
            .padding(.horizontal, PocketMetrics.screenMargin)
        }
        .preferredColorScheme(.dark)
        .task(id: phase) {
            guard phase == .restoring else { return }
            try? await Task.sleep(for: PocketMotion.indicatorGrace)
            withAnimation(PocketMotion.quick) { showsSpinner = true }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("PocketCounter")
        .accessibilityValue(phase == .restoring && showsSpinner ? "Carregando" : "")
    }

    @ViewBuilder
    private func unreadable(offersEscape: Bool, isRetrying: Bool) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .pocketFont(PocketFont.notice)
                .foregroundStyle(PocketColor.onHero)
                .accessibilityHidden(true)

            // Not "erro": nothing is broken, and the session is not gone.
            Text("Não conseguimos abrir sua sessão")
                .pocketFont(PocketFont.sectionTitle)
                .foregroundStyle(PocketColor.onHero)
                .multilineTextAlignment(.center)

            Text("Isso costuma ser temporário. Seus dados continuam salvos.")
                .pocketFont(PocketFont.subtitle)
                .foregroundStyle(PocketColor.onHero.opacity(0.75))
                .multilineTextAlignment(.center)

            PocketPrimaryButton("Tentar novamente", role: .onHero, isLoading: isRetrying, action: onRetry)
                .padding(.top, 8)

            if offersEscape {
                // The user's own choice, not the gate deciding. Discards nothing: navigating to
                // Login never clears tokens, and abandoning it leaves the session for next launch.
                Button("Entrar com e-mail e senha", action: onUsePassword)
                    .pocketFont(PocketFont.link)
                    .foregroundStyle(PocketColor.onHero)
                    .padding(.top, 4)
                    .disabled(isRetrying)
            }
        }
    }
}

#if DEBUG
#Preview("Restoring") { LaunchSplash(phase: .restoring) }

#Preview("Unreadable") {
    LaunchSplash(phase: .unreadable(offersEscape: false, isRetrying: false))
}

#Preview("Unreadable, escape offered") {
    LaunchSplash(phase: .unreadable(offersEscape: true, isRetrying: false))
}

#Preview("Retrying") {
    LaunchSplash(phase: .unreadable(offersEscape: true, isRetrying: true))
}
#endif
