/// What an auth screen renders for a failure. Severity is a value, not a colour: the view
/// picks a symbol from it, so it never rests on hue alone.
struct AuthMessage: Equatable, Sendable {
    enum Kind: Equatable, Sendable { case error, offline }
    enum Action: Equatable, Sendable { case signInWithThisAccount }

    let kind: Kind
    let text: String
    let secondary: String?
    let action: Action?

    /// The design-system kind that carries this severity.
    var displayKind: PocketInlineMessage.Kind {
        switch kind {
        case .error: .error
        case .offline: .offline
        }
    }

    init(kind: Kind, text: String, secondary: String? = nil, action: Action? = nil) {
        self.kind = kind
        self.text = text
        self.secondary = secondary
        self.action = action
    }
}
