import Foundation

/// pt-BR copy for a sentence the server could not read. `nil` renders nothing.
enum ReadingFailureMessage {
    private static let title = "Não foi possível ler a frase"

    static func message(for failure: ReadingFailure) -> PocketNotice? {
        switch failure {
        case .sessionExpired:
            return nil
        case .unreachable:
            return PocketNotice(kind: .offline, title: title, detail: "Sem conexão com o servidor.")
        case .tooManyRequests:
            return PocketNotice(kind: .warning, title: title, detail: "Muitas tentativas seguidas. Espere um minuto.")
        case .server:
            return PocketNotice(kind: .error, title: title, detail: "Tente de novo em instantes.")
        case .authenticationUnavailable:
            return PocketNotice(kind: .info, title: title, detail: TransientFailureCopy.reassurance)
        case .rejected(let text):
            return PocketNotice(
                kind: .error, title: title, detail: ServerText.presentable(text) ?? "O servidor recusou a frase.")
        }
    }
}
