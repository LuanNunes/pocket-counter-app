import Foundation

/// pt-BR copy for a failed write. `nil` renders nothing.
enum WriteFailureMessage {
    private static let title = "Não foi possível salvar"

    static func message(for failure: WriteFailure) -> PocketNotice? {
        switch failure {
        case .sessionExpired:
            return nil
        case .unreachable:
            return PocketNotice(kind: .offline, title: title, detail: "Sem conexão com o servidor.")
        case .server:
            return PocketNotice(kind: .error, title: title, detail: "Tente de novo em instantes.")
        case .authenticationUnavailable:
            return PocketNotice(
                kind: .info, title: title, detail: TransientFailureCopy.reassurance)
        case .rejected(let text):
            return PocketNotice(
                kind: .error, title: title, detail: ServerText.presentable(text) ?? "O servidor recusou a alteração.")
        case .vanished:
            return PocketNotice(
                kind: .error, title: "Este lançamento não existe mais", detail: "Pode ter sido excluído em outro aparelho.")
        }
    }
}
