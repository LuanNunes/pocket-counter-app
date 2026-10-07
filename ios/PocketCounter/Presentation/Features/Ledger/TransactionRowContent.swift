import Foundation

/// A transaction row's text and facts, resolved against the lookups.
struct TransactionRowContent: Equatable {
    struct TagChip: Equatable {
        let name: String
        let argb: UInt32?
    }

    let title: String
    let isFixo: Bool
    let isPaid: Bool
    let amount: Decimal
    let kind: TransactionType
    let tag: TagChip?
    let extraTags: Int
    let payLabel: String?

    static func of(_ item: HistoryItem, lookups: LookupSet) -> TransactionRowContent {
        let tagIds = item.effectiveTagIds(inheriting: [])
        return TransactionRowContent(
            title: item.displayTitle(),
            isFixo: item.isFixo,
            isPaid: item.statusPayment == .paid,
            amount: item.amount.amount,
            kind: item.type,
            tag: tagIds.first.map { chip(for: $0, lookups: lookups) },
            extraTags: max(tagIds.count - 1, 0),
            payLabel: payLabel(item, lookups: lookups)
        )
    }

    static func chip(for id: TagID, lookups: LookupSet) -> TagChip {
        guard let tag = lookups.tagsById[id] else { return TagChip(name: TransactionsCopy.unresolvedGroup, argb: nil) }
        return TagChip(name: tag.name, argb: tag.color)
    }

    static func payLabel(_ item: HistoryItem, lookups: LookupSet) -> String? {
        guard let method = item.paymentMethod else { return nil }
        guard method == .credit else { return TransactionsCopy.methodName(method) }
        guard let name = item.cardId.flatMap({ lookups.cardsById[$0]?.name }) else {
            return TransactionsCopy.methodName(.credit)
        }
        return name.hasPrefix("Cartão ") ? String(name.dropFirst("Cartão ".count)) : name
    }
}
