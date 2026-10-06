import Foundation

enum HomeCopy {
    static let unknownFigure = "—"

    static func cardCount(_ count: Int?) -> String {
        guard let count else { return unknownFigure }
        return count == 1 ? "1 cartão" : "\(count) cartões"
    }

    /// For VoiceOver, which would read the dash as nothing.
    static func cardCountLabel(_ count: Int?) -> String {
        guard let count else { return "Número de cartões indisponível" }
        return cardCount(count)
    }

    static func transactionCount(_ count: Int) -> String { "\(count) no mês" }

    static func pendingCount(_ count: Int) -> String { "\(count) em aberto" }

    static func entryCount(_ count: Int) -> String { count == 1 ? "1 lanç." : "\(count) lançs." }

    static func spokenEntryCount(_ count: Int) -> String {
        count == 1 ? "1 lançamento" : "\(count) lançamentos"
    }
}
