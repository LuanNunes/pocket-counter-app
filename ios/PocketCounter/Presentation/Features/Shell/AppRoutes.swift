import Foundation

enum TabRoute: Hashable { case inicio, transacoes, cartoes, mais }

/// The screen states an intent; only the shell knows it means a tab change.
typealias SelectTabAction = @MainActor (TabRoute) -> Void

/// One enum per `NavigationStack`: SwiftUI cannot restrict which cases a destination accepts,
/// so a separate type is the only way to keep one stack's routes out of another.
enum HomeRoute: Hashable { case report }

enum MoreRoute: Hashable {
    case report, categories, shortcuts
    #if DEBUG
    case gallery
    #endif
}
