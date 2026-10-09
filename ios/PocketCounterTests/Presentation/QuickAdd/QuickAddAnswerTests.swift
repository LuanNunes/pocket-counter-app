import Foundation
import Testing

@testable import PocketCounter

@Suite("QuickAddAnswer")
struct QuickAddAnswerTests {
    @Test("an amount is usable in either separator and never when it is zero or garbage")
    func amount() {
        #expect(QuickAddAnswer.amount("12,50")?.amount == Decimal(string: "12.5"))
        #expect(QuickAddAnswer.amount("12.50")?.amount == Decimal(string: "12.5"))
        #expect(QuickAddAnswer.amount("0") == nil)
        #expect(QuickAddAnswer.amount("") == nil)
        #expect(QuickAddAnswer.amount("abc") == nil)
    }

    @Test("a name is trimmed, and blank or over-long is refused")
    func name() {
        #expect(QuickAddAnswer.name("  Padaria ") == "Padaria")
        #expect(QuickAddAnswer.name("   ") == nil)
        #expect(QuickAddAnswer.name(String(repeating: "a", count: TransactionEntry.maxNameUTF16Units + 1)) == nil)
    }

    @Test("a sentence is sendable from two characters, and not when blank or over the limit")
    func sentence() {
        #expect(QuickAddAnswer.isSendable("ab"))
        #expect(!QuickAddAnswer.isSendable("a"))
        #expect(!QuickAddAnswer.isSendable("   a  "))
        #expect(!QuickAddAnswer.isSendable(String(repeating: "a", count: SentenceText.maxUTF16Units + 1)))
    }
}
