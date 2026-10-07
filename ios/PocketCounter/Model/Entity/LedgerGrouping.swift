import Foundation

enum LedgerGroupMode: Hashable, Sendable, CaseIterable { case lista, categoria, tag }

enum LedgerGroupIdentity: Hashable, Sendable {
    case day(CalendarDay), category(ContextID?), tag(TagID?), incomeName(String), unresolved
}

struct LedgerGroup: Identifiable, Hashable, Sendable {
    let identity: LedgerGroupIdentity
    let items: [HistoryItem]
    let subtotal: Money

    var id: LedgerGroupIdentity { identity }
}

enum LedgerGrouping {
    /// A row is placed by its first effective tag and never duplicated.
    static func groups(
        of items: [HistoryItem], lookups: LookupSet, mode: LedgerGroupMode, kind: TransactionType
    ) -> [LedgerGroup] {
        switch (mode, kind) {
        case (.lista, _): days(items)
        // Income groups by title, as `docs/ios26/tx.jsx` does.
        case (.categoria, .income): incomeNames(items)
        case (.categoria, .expense): categories(items, lookups: lookups)
        case (.tag, _): tags(items, lookups: lookups)
        }
    }

    private static func days(_ items: [HistoryItem]) -> [LedgerGroup] {
        Dictionary(grouping: items, by: \.date)
            .sorted { $0.key > $1.key }
            .map { day, rows in group(.day(day), rows) }
    }

    private static func incomeNames(_ items: [HistoryItem]) -> [LedgerGroup] {
        let groups = Dictionary(grouping: items) { LedgerGroupIdentity.incomeName($0.displayTitle()) }
            .map { identity, rows in group(identity, rows) }
        return ordered(groups) { group in
            switch group.identity {
            case .incomeName(let name): Position(section: 0, weight: -group.subtotal.amount, name: name)
            case .day, .category, .tag, .unresolved: Position(section: 1)
            }
        }
    }

    private static func categories(_ items: [HistoryItem], lookups: LookupSet) -> [LedgerGroup] {
        let degraded = !lookups.failed.isDisjoint(with: [.tags, .categories])
        let groups = Dictionary(grouping: items) { item -> LedgerGroupIdentity in
            guard let tagId = item.effectiveTagIds(inheriting: []).first else { return .category(nil) }
            guard !degraded else { return .unresolved }
            guard let tag = lookups.tagsById[tagId] else { return .unresolved }
            return .category(tag.contextId)
        }.map { identity, rows in group(identity, rows) }
        return ordered(groups) { group in
            switch group.identity {
            case .category(let id?):
                Position(section: 0, rank: lookups.categories.firstIndex { $0.id == id } ?? lookups.categories.count, id: id.rawValue)
            case .category(nil): Position(section: 1)
            case .unresolved: Position(section: 2)
            case .day, .tag, .incomeName: Position(section: 3)
            }
        }
    }

    private static func tags(_ items: [HistoryItem], lookups: LookupSet) -> [LedgerGroup] {
        let degraded = lookups.failed.contains(.tags)
        let groups = Dictionary(grouping: items) { item -> LedgerGroupIdentity in
            guard let tagId = item.effectiveTagIds(inheriting: []).first else { return .tag(nil) }
            guard !degraded else { return .unresolved }
            return .tag(tagId)
        }.map { identity, rows in group(identity, rows) }
        return ordered(groups) { group in
            switch group.identity {
            case .tag(let id?):
                let tag = lookups.tagsById[id]
                let rank = tag?.contextId.flatMap { context in lookups.categories.firstIndex { $0.id == context } }
                return Position(section: 0, rank: rank ?? lookups.categories.count, name: tag?.name ?? "", id: id.rawValue)
            case .tag(nil): return Position(section: 1)
            case .unresolved: return Position(section: 2)
            case .day, .category, .incomeName: return Position(section: 3)
            }
        }
    }

    private static func group(_ identity: LedgerGroupIdentity, _ items: [HistoryItem]) -> LedgerGroup {
        LedgerGroup(identity: identity, items: items, subtotal: items.map(\.amount.abs).sum())
    }

    private static func ordered(_ groups: [LedgerGroup], by position: (LedgerGroup) -> Position) -> [LedgerGroup] {
        groups.map { (position($0), $0) }.sorted { $0.0 < $1.0 }.map(\.1)
    }

    /// Total only because `ReadingOrder.compare` forces an ordering on equal names.
    private struct Position: Comparable {
        let section: Int
        var rank = 0
        var weight = Decimal.zero
        var name = ""
        var id = ""

        static func < (lhs: Position, rhs: Position) -> Bool {
            guard lhs.section == rhs.section else { return lhs.section < rhs.section }
            guard lhs.rank == rhs.rank else { return lhs.rank < rhs.rank }
            guard lhs.weight == rhs.weight else { return lhs.weight < rhs.weight }
            switch ReadingOrder.compare(lhs.name, rhs.name, locale: ReadingOrder.locale) {
            case .orderedAscending: return true
            case .orderedDescending: return false
            case .orderedSame: return lhs.id < rhs.id
            }
        }
    }
}
