import Foundation

/// The user-facing text of a 403 or a 400/422, shared by reads and writes.
enum RejectionText {
    static func forbidden(_ server: ServerMessage?) -> String {
        text(server?.message) ?? "Você não tem acesso a este recurso"
    }

    static func unprocessable(_ server: ServerMessage?) -> String {
        text(server?.details.first) ?? text(server?.message) ?? "Não foi possível processar a solicitação"
    }

    /// Never reads `details`: on a 409 `details[0]` is the existing row's UUID, not text.
    static func conflict(_ server: ServerMessage?) -> String {
        text(server?.message) ?? "Já existe um lançamento igual neste mês"
    }

    private static func text(_ value: String?) -> String? {
        guard let value, !value.isEmpty else { return nil }
        return value
    }
}
