import Foundation

/// A repository that caches lookups. Whatever changes them or ends the session calls this.
protocol LookupCaching: Sendable {
    func invalidateLookups() async
}
