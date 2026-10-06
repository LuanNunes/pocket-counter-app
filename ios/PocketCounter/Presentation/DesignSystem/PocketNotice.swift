/// A message the design system can show, whatever produced it.
struct PocketNotice: Equatable, Sendable {
    let kind: PocketInlineMessage.Kind
    let title: String
    let detail: String?
}
