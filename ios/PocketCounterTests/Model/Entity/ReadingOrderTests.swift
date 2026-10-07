import Foundation
import Testing

@testable import PocketCounter

@Suite("ReadingOrder")
struct ReadingOrderTests {
    private func order(_ names: [String], locale: Locale = ReadingOrder.locale) -> [String] {
        ReadingOrder.byName(names, locale: locale, name: { $0 }, id: { $0 })
    }

    @Test("it collates in pt-BR whatever the device locale")
    func pinnedToPortugueseBrazil() {
        #expect(ReadingOrder.locale == Locale(identifier: "pt_BR"))
        #expect(order(["Zebra", "Äpple", "Apple"]) == ["Apple", "Äpple", "Zebra"])
    }

    @Test("the locale decides the collation, so pinning it matters")
    func localeIsHonoured() {
        #expect(order(["Zebra", "Äpple", "Apple"], locale: Locale(identifier: "sv")) == ["Apple", "Zebra", "Äpple"])
    }

    @Test("numbers inside a name compare by value, ignoring case and accents")
    func numericAndInsensitive() {
        #expect(order(["Item 10", "item 2", "Água"]) == ["Água", "item 2", "Item 10"])
    }

    @Test("equal names fall to the id")
    func idTieBreak() {
        let ordered = ReadingOrder.byName([("b", "Mercado"), ("a", "Mercado")], name: { $0.1 }, id: { $0.0 })

        #expect(ordered.map(\.0) == ["a", "b"])
    }
}
