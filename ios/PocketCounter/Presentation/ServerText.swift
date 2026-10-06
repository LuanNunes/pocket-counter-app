import Foundation

/// The one filter that decides what server text reaches a user.
enum ServerText {
    private static let sentinels: Set<String> = [
        "Domain exception occurred", "Validation failed", "An unexpected error occurred", "Invalid request parameter",
    ]

    /// `nil` when the text is empty, a message-bundle key or a framework sentinel. No language
    /// detection: guessing a one-line string's language would suppress legitimate pt-BR copy.
    static func presentable(_ payload: String) -> String? {
        let trimmed = payload.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !looksLikeBundleKey(trimmed), !sentinels.contains(trimmed) else { return nil }
        return trimmed
    }

    private static func looksLikeBundleKey(_ text: String) -> Bool {
        text.contains(".") && !text.contains(" ") && text.count > 8
    }
}
