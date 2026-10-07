import SwiftUI

/// A notice that sits among a screen's sections, over data that is still on screen.
struct PocketNoticeCard: View {
    let notice: PocketNotice
    var action: PocketInlineMessage.Action?
    var isBusy = false

    var body: some View {
        PocketListSection {
            PocketInlineMessage(
                kind: notice.kind, text: notice.title, secondary: notice.detail,
                action: action, isBusy: isBusy, surface: .onCell
            )
            .padding(.horizontal, PocketMetrics.rowPaddingH)
            .padding(.vertical, PocketMetrics.rowPaddingV)
        }
    }
}

#if DEBUG
#Preview("Notice card") {
    VStack(spacing: 16) {
        PocketNoticeCard(
            notice: PocketNotice(kind: .error, title: "Algo deu errado", detail: "Mostrando os dados anteriores."),
            action: .init(title: "Tentar novamente") {}
        )
        PocketNoticeCard(
            notice: PocketNotice(kind: .warning, title: "As tags não carregaram",
                               detail: "Os valores estão certos; só os nomes estão faltando.")
        )
    }
    .frame(maxHeight: .infinity, alignment: .top)
    .background(PocketColor.background)
}
#endif
