import SwiftUI

/// The inline message slot that sits directly below a field group — never an alert (it steals
/// focus and dismisses the keyboard) and never a toast (`.toast` is for confirmations).
///
/// On the hero the symbol and text are **white**, 11.52:1 on `heroBase` and 6.64:1 under the
/// highlight. Severity is carried by the symbol, the position under the fields and the VoiceOver
/// announcement — never by hue, because `destructiveInk` measures 3.38:1 on `heroBase` and 1.95:1
/// under the highlight. `.onCell` is the in-shell presentation, where the ink pairs are the right
/// answer and `destructiveInk` is 5.00:1; it is the only presentation that may use a coloured ink,
/// and only off the gradient.
struct PocketInlineMessage: View {

    enum Kind {
        case error
        case offline
        case info
    }

    enum Surface {
        /// On the hero gradient: white only.
        case onHero
        /// On `PocketColor.cell`, in the shell.
        case onCell
    }

    /// A next step we actually know, such as `Entrar nesta conta` after a duplicate e-mail.
    struct Action {
        let title: String
        let perform: () -> Void
    }

    var kind: Kind
    var text: String
    var secondary: String?
    var action: Action?
    var surface: Surface = .onHero

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Image(systemName: symbol)
                    .font(PocketFont.body.font)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 2) {
                    Text(text)
                        .pocketFont(PocketFont.subtitle)

                    if let secondary {
                        Text(secondary)
                            .pocketFont(PocketFont.caption)
                            .foregroundStyle(secondaryInk)
                    }
                }
                .fixedSize(horizontal: false, vertical: true)

                Spacer(minLength: 0)
            }
            .foregroundStyle(primaryInk)
            .accessibilityElement(children: .combine)

            if let action {
                Button(action.title, action: action.perform)
                    .pocketFont(PocketFont.link)
                    .foregroundStyle(primaryInk)
                    .padding(.vertical, 12)
                    .contentShape(.rect)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 4)
        .transition(transition)
        .onAppear { announce() }
        .onChange(of: text) { announce() }
    }

    /// An inline message is a silent change for a VoiceOver user, so it has to say itself.
    private func announce() {
        AccessibilityNotification.Announcement(announcement).post()
    }

    private var announcement: String {
        guard let secondary else { return text }

        return "\(text). \(secondary)"
    }

    private var symbol: String {
        switch kind {
        case .error: "exclamationmark.triangle.fill"
        case .offline: "wifi.slash"
        case .info: "info.circle"
        }
    }

    private var primaryInk: Color {
        switch surface {
        case .onHero: PocketColor.onHero
        case .onCell: cellInk
        }
    }

    private var cellInk: Color {
        switch kind {
        case .error: PocketColor.destructiveInk
        case .offline: PocketColor.warningInk
        case .info: PocketColor.labelSecondary
        }
    }

    private var secondaryInk: Color {
        switch surface {
        case .onHero: PocketColor.onHero.opacity(0.75)
        case .onCell: PocketColor.labelSecondary
        }
    }

    private var transition: AnyTransition {
        guard !reduceMotion else { return .opacity }

        return .opacity.combined(with: .move(edge: .top))
    }
}

#if DEBUG
private struct InlineMessagePreview<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            content
        }
        .padding(PocketMetrics.screenMargin)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background { PocketColor.heroSurface.ignoresSafeArea() }
        .preferredColorScheme(.dark)
    }
}

#Preview("On hero") {
    InlineMessagePreview {
        PocketInlineMessage(kind: .error, text: "E-mail ou senha incorretos")
        PocketInlineMessage(kind: .offline, text: "Sem conexão com o servidor")
        PocketInlineMessage(kind: .error, text: "Erro inesperado")
    }
}

#Preview("On hero · second line") {
    InlineMessagePreview {
        PocketInlineMessage(
            kind: .error,
            text: "E-mail ou senha incorretos",
            secondary: "A recuperação de senha ainda não está disponível neste app."
        )
    }
}

#Preview("On hero · with action") {
    InlineMessagePreview {
        PocketInlineMessage(
            kind: .error,
            text: "Este e-mail já está cadastrado",
            action: .init(title: "Entrar nesta conta") {}
        )
    }
}

#Preview("On hero · AX5") {
    InlineMessagePreview {
        PocketInlineMessage(
            kind: .error,
            text: "E-mail ou senha incorretos",
            secondary: "A recuperação de senha ainda não está disponível neste app."
        )
    }
    .environment(\.dynamicTypeSize, .accessibility5)
}

#Preview("On cell") {
    VStack(alignment: .leading, spacing: 20) {
        PocketInlineMessage(kind: .error, text: "Não foi possível encerrar a sessão agora",
                            surface: .onCell)
        PocketInlineMessage(kind: .offline, text: "Sem conexão com o servidor", surface: .onCell)
        PocketInlineMessage(kind: .info, text: "Seus dados continuam salvos.", surface: .onCell)
    }
    .padding(PocketMetrics.screenMargin)
    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    .background(PocketColor.cell)
}
#endif
