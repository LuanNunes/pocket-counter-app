import Foundation

/// What a typed answer is worth. The footer's "Confirmar" is live only when this says yes.
enum QuickAddAnswer {
    static func amount(_ typed: String) -> Money? {
        AmountEntry.money(typed)
    }

    /// Trimmed, and within what `TransactionEntry` accepts: a longer name would pass the question
    /// and leave a review that can never be confirmed.
    static func name(_ typed: String) -> String? {
        let trimmed = typed.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.utf16.count <= TransactionEntry.maxNameUTF16Units else { return nil }
        return trimmed
    }

    /// Stricter than `SentenceText` (a single character is not a sentence), so a disabled button is the only refusal.
    static func isSendable(_ sentence: String) -> Bool {
        guard sentence.trimmingCharacters(in: .whitespacesAndNewlines).count > 1 else { return false }
        return (try? SentenceText(sentence)) != nil
    }
}
