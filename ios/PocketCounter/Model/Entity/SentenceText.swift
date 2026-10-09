import Foundation

enum SentenceTextError: Error, Equatable, Sendable {
    case blank
    case tooLong(maximum: Int)

    var message: String {
        switch self {
        case .blank:
            "Escreva o lançamento"
        case .tooLong(let maximum):
            "O texto é longo demais (máximo de \(maximum) caracteres)"
        }
    }
}

/// A sentence worth sending: non-blank and within the server's limit.
struct SentenceText: Hashable, Sendable {
    static let maxUTF16Units = 500

    let value: String

    init(_ raw: String) throws(SentenceTextError) {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw .blank }
        // UTF-16 units: the backend's Kotlin `String.length`, so 251 emoji fail there, not here.
        guard trimmed.utf16.count <= Self.maxUTF16Units else { throw .tooLong(maximum: Self.maxUTF16Units) }
        value = trimmed
    }
}
