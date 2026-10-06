import Foundation


/// Which sign-in path failed. `AuthenticationFailure` is path-blind on purpose: a 401 from
/// `/auth/google` maps to `.invalidCredentials` too, and "e-mail ou senha incorretos" is
/// false for a user who never typed a password.
enum AuthPath: Equatable, Sendable { case password, google, register }

/// pt-BR copy and the "render nothing" rule — a rendering concern, so it lives here and not in `Model/`.
enum AuthFailureMessage {
    static let passwordRecoveryUnavailable = "A recuperação de senha ainda não está disponível neste app."

    private static let generic = "Não foi possível concluir. Revise os dados e tente novamente"
    private static let google = "Não foi possível entrar com o Google"

    /// `nil` renders nothing.
    static func text(for failure: AuthenticationFailure, path: AuthPath) -> AuthMessage? {
        switch failure {
        case .abandoned:
            return nil
        case .unreachable:
            return AuthMessage(kind: .offline, text: "Sem conexão com o servidor")
        case .invalidCredentials, .emailAlreadyRegistered, .rejected, .server:
            break
        }
        // One message for the SDK failing, a 401 and an audience mismatch: telling them apart
        // would leak deployment configuration. That distinction belongs in the logs.
        guard path != .google else { return AuthMessage(kind: .error, text: google) }

        switch failure {
        case .invalidCredentials:
            return AuthMessage(kind: .error, text: "E-mail ou senha incorretos")
        case .emailAlreadyRegistered:
            return AuthMessage(
                kind: .error, text: "Este e-mail já está cadastrado",
                action: path == .register ? .signInWithThisAccount : nil
            )
        case .rejected(let payload):
            // A short legacy password makes /auth/login answer 400, whose text ("A senha precisa
            // ter ao menos 8 caracteres") reads oddly on a sign-in screen. Showing the server's
            // text anyway is the deliberate choice — no special case maps it to "incorretos".
            return AuthMessage(kind: .error, text: ServerText.presentable(payload) ?? generic)
        case .server:
            return AuthMessage(kind: .error, text: "Erro inesperado")
        case .abandoned, .unreachable:
            return nil
        }
    }
}
