import Testing

@testable import PocketCounter

@Suite("GroupOrder")
struct GroupOrderTests {
    private let order = GroupOrder(ids: ["a", "b", "c"].map { TransactionID(rawValue: $0) })

    private func id(_ raw: String) -> TransactionID { TransactionID(rawValue: raw) }
    private func raw(_ ids: [TransactionID]?) -> [String]? { ids?.map(\.rawValue) }

    @Test("moving up swaps with the row above")
    func up() {
        #expect(raw(order.movingUp(id("b"))) == ["b", "a", "c"])
        #expect(raw(order.movingUp(id("c"))) == ["a", "c", "b"])
    }

    @Test("moving down swaps with the row below")
    func down() {
        #expect(raw(order.movingDown(id("a"))) == ["b", "a", "c"])
        #expect(raw(order.movingDown(id("b"))) == ["a", "c", "b"])
    }

    @Test("the first row cannot go up and the last cannot go down")
    func edges() {
        #expect(order.movingUp(id("a")) == nil)
        #expect(order.movingDown(id("c")) == nil)
    }

    @Test("an unknown row has no move")
    func unknown() {
        #expect(order.movingUp(id("z")) == nil)
        #expect(order.movingDown(id("z")) == nil)
    }

    @Test("a single row has nowhere to go")
    func single() {
        let one = GroupOrder(ids: [id("a")])
        #expect(one.movingUp(id("a")) == nil)
        #expect(one.movingDown(id("a")) == nil)
    }
}
