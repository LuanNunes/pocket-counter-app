import Foundation

/// pt-BR copy for a failed write. `nil` renders nothing.
enum WriteFailureMessage {
    /// What the failed write was doing; only the title depends on it.
    enum Subject: Equatable, Sendable {
        case saving, deleting
    }

    static func message(for failure: WriteFailure, subject: Subject) -> PocketNotice? {
        let title = title(for: subject)
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

    private static func title(for subject: Subject) -> String {
        switch subject {
        case .saving: "Não foi possível salvar"
        case .deleting: "Não foi possível excluir"
        }
    }
}
