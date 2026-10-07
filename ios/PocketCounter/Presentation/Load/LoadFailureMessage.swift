import Foundation

/// pt-BR copy for a failed load. `nil` renders nothing.
enum LoadFailureMessage {
    private static let fallback = "Não foi possível carregar"
    private static let stale = "Mostrando os dados anteriores."

    /// There is nothing else to show.
    static func blocking(for failure: LoadFailure) -> PocketNotice? {
        switch failure {
        case .sessionExpired, .abandoned:
            return nil
        case .unreachable:
            return PocketNotice(
                kind: .offline, title: "Sem conexão com o servidor", detail: "Verifique sua internet e tente de novo.")
        case .server:
            return PocketNotice(
                kind: .error, title: "Algo deu errado",
                detail: "O servidor não conseguiu responder. Tente de novo em instantes.")
        case .authenticationUnavailable:
            return PocketNotice(
                kind: .info, title: "Não conseguimos confirmar sua sessão",
                detail: TransientFailureCopy.reassurance)
        case .notFound:
            return PocketNotice(kind: .error, title: "Não encontramos esses dados", detail: "Eles podem ter sido removidos.")
        case .rejected(let text):
            return PocketNotice(kind: .error, title: ServerText.presentable(text) ?? fallback, detail: nil)
        }
    }

    /// Over data that is still on screen.
    static func notice(for failure: LoadFailure) -> PocketNotice? {
        guard let blocking = blocking(for: failure) else { return nil }
        return PocketNotice(kind: blocking.kind, title: blocking.title, detail: stale)
    }

    /// A `Set` has no order, and the copy must be deterministic.
    static func degraded(_ kinds: Set<LookupKind>) -> PocketNotice? {
        let names = [
            (LookupKind.categories, "as categorias"), (.tags, "as tags"), (.cards, "os cartões"),
        ].filter { kinds.contains($0.0) }.map(\.1)
        guard let last = names.last else { return nil }

        let head = names.dropLast().joined(separator: ", ")
        let joined = head.isEmpty ? last : "\(head) e \(last)"
        return PocketNotice(
            kind: .warning,
            title: joined.prefix(1).uppercased() + joined.dropFirst() + " não carregaram",
            detail: "Os valores estão certos; só os nomes estão faltando."
        )
    }
}
