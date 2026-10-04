import Foundation

struct AuthenticatedUser: Equatable, Sendable {
    let id: UserID
    let displayName: String
    let email: String

    init(id: UserID, name: String?, email: String) {
        let trimmed = name?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        self.id = id
        self.displayName = trimmed.isEmpty ? email : trimmed
        self.email = email
    }
}
