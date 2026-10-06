import Testing

@testable import PocketCounter

@Suite("InvoiceItem")
struct InvoiceItemTests {

    @Test("no tags is an empty list, not an absence")
    func noTags() {
        let item = InvoiceItem(
            id: InvoiceItemID(rawValue: "i"), invoiceId: TransactionID(rawValue: "t"), name: "Uber",
            amount: Money(-20), purchasedOn: nil, originName: nil, tagIds: []
        )

        #expect(item.tagIds.isEmpty)
        #expect(item.amount == Money(-20))
    }
}
