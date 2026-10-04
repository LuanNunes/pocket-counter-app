import Foundation

struct AuthenticatedUser: Equatable, Sendable {
    let displayName: String
    let email: String

    init(name: String?, email: String) {
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.displayName = trimmed.isEmpty ? email : trimmed
        self.email = email
    }
}
