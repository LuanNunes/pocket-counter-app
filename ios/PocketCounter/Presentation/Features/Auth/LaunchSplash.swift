import SwiftUI

/// Launch and "could not read the session". Same composition either way, so one becoming the
/// other does not recompose jarringly; `.unreadable` is distinct **by addition**.
struct LaunchSplash: View {
    enum Phase: Equatable {
        case restoring
        /// `attempts` drives the escape hatch, revealed from the second failure on.
        case unreadable(attempts: Int, isRetrying: Bool)
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
                case .unreadable(let attempts, let isRetrying):
                    unreadable(attempts: attempts, isRetrying: isRetrying)
                }
            }
            .padding(.horizontal, PocketMetrics.screenMargin)
        }
        .preferredColorScheme(.dark)
        .task(id: phase) {
            guard phase == .restoring else { return }
            try? await Task.sleep(for: .milliseconds(600))
            withAnimation(.easeOut(duration: 0.2)) { showsSpinner = true }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("PocketCounter")
        .accessibilityValue(phase == .restoring && showsSpinner ? "Carregando" : "")
    }

    @ViewBuilder
    private func unreadable(attempts: Int, isRetrying: Bool) -> some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 28))
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

            if attempts >= 2 {
                // The user's own choice, not the gate deciding. Discards nothing: navigating to
                // Login never clears tokens, and abandoning it leaves the session for next launch.
                Button("Entrar com e-mail e senha", action: onUsePassword)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(PocketColor.onHero)
                    .padding(.top, 4)
            }
        }
    }
}

#if DEBUG
#Preview("Restoring") { LaunchSplash(phase: .restoring) }

#Preview("Unreadable") {
    LaunchSplash(phase: .unreadable(attempts: 1, isRetrying: false))
}

#Preview("Unreadable, escape offered") {
    LaunchSplash(phase: .unreadable(attempts: 2, isRetrying: false))
}

#Preview("Retrying") {
    LaunchSplash(phase: .unreadable(attempts: 2, isRetrying: true))
}
#endif
