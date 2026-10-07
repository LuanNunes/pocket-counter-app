enum LedgerReorder {
    /// Writes `group`'s order back into the slots its rows occupy in `all`; every other slot stays.
    /// `all` must be the complete order the group belongs to. `group` must be in ascending-slot
    /// order, which holds because `LedgerGrouping` keeps `LedgerOrder` within each bucket.
    static func placing(_ group: [TransactionID], into all: [TransactionID]) -> [TransactionID] {
        let held = Set(all)
        let group = group.filter(held.contains)
        let slots = all.indices.filter { group.contains(all[$0]) }
        var result = all
        for (slot, id) in zip(slots, group) { result[slot] = id }
        return result
    }
}
