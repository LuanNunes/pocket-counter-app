import Testing

@testable import PocketCounter

@Suite("LedgerReorder")
struct LedgerReorderTests {
    private func ids(_ raws: String...) -> [TransactionID] {
        raws.map { TransactionID(rawValue: $0) }
    }

    @Test("a contiguous group is written back in its own order")
    func contiguous() {
        let result = LedgerReorder.placing(ids("c", "b"), into: ids("a", "b", "c", "d"))

        #expect(result == ids("a", "c", "b", "d"))
    }

    @Test("a non-contiguous group swaps across the rows between its slots")
    func nonContiguous() {
        let result = LedgerReorder.placing(ids("c", "a"), into: ids("a", "b", "c", "d"))

        #expect(result == ids("c", "b", "a", "d"))
    }

    @Test("an id the ledger no longer holds is skipped without losing a slot")
    func absentId() {
        let result = LedgerReorder.placing(ids("gone", "c", "a"), into: ids("a", "b", "c", "d"))

        #expect(result == ids("c", "b", "a", "d"))
    }

    @Test("placing the whole order into itself changes nothing")
    func identity() {
        let all = ids("a", "b", "c", "d")

        #expect(LedgerReorder.placing(all, into: all) == all)
    }
}
