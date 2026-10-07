import Foundation

/// What a group header says and the colour of its dot, as data: no SwiftUI, so it is testable.
struct LedgerGroupLabel: Equatable {
    let name: String
    let argb: UInt32?

    static func of(_ identity: LedgerGroupIdentity, lookups: LookupSet) -> LedgerGroupLabel {
        switch identity {
        case .day(let day):
            return LedgerGroupLabel(name: PocketFormat.dayLabel(day), argb: nil)
        case .category(let id?):
            guard let context = lookups.categoriesById[id] else {
                return LedgerGroupLabel(name: TransactionsCopy.unresolvedGroup, argb: nil)
            }
            return LedgerGroupLabel(name: context.name, argb: context.color)
        case .category(nil):
            return LedgerGroupLabel(name: TransactionsCopy.uncategorised, argb: nil)
        case .tag(let id?):
            guard let tag = lookups.tagsById[id] else {
                return LedgerGroupLabel(name: TransactionsCopy.unresolvedGroup, argb: nil)
            }
            return LedgerGroupLabel(name: tag.name, argb: tag.color)
        case .tag(nil):
            return LedgerGroupLabel(name: TransactionsCopy.untagged, argb: nil)
        case .incomeName(let name):
            return LedgerGroupLabel(name: name, argb: nil)
        case .unresolved:
            return LedgerGroupLabel(name: TransactionsCopy.unresolvedGroup, argb: nil)
        }
    }
}
