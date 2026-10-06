import Testing

@testable import PocketCounter

@Suite("Typed identifiers")
struct IdentifierTests {

    @Test("ids wrap their raw string and compare by value")
    func equalityByValue() {
        #expect(TagID(rawValue: "a") == TagID(rawValue: "a"))
        #expect(TagID(rawValue: "a") != TagID(rawValue: "b"))
        #expect(Set([CardID(rawValue: "x"), CardID(rawValue: "x")]).count == 1)
        #expect(TransactionID(rawValue: "t").rawValue == "t")
        #expect(ContextID(rawValue: "c").rawValue == "c")
        #expect(InvoiceItemID(rawValue: "i").rawValue == "i")
    }
}
