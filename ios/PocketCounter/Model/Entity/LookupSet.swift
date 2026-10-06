import Foundation

enum LookupKind: Hashable, Sendable {
    case tags, categories, cards
}

struct LookupSet: Hashable, Sendable {
    /// Display order, which groups are rendered in.
    let categories: [TagContext]
    let tags: [Tag]
    let cards: [CreditCard]
    /// Lookups that could not load; an empty list here means failed, not absent.
    let failed: Set<LookupKind>
    let categoriesById: [ContextID: TagContext]
    let tagsById: [TagID: Tag]
    let cardsById: [CardID: CreditCard]

    init(categories: [TagContext], tags: [Tag], cards: [CreditCard], failed: Set<LookupKind> = []) {
        self.categories = categories
        self.tags = tags
        self.cards = cards
        self.failed = failed
        categoriesById = Dictionary(categories.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        tagsById = Dictionary(tags.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        cardsById = Dictionary(cards.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
    }
}
