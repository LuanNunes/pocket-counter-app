import Foundation

extension RowWrite where Target == RowIntent {
    var verb: RowIntent? { projection ?? attempted }
}

/// A status write projects over the server's answer. An intent projects only into the control
/// that started it, except a settled deletion, which hides its row.
typealias PaymentStatusWrite = RowWrite<PaymentStatus>
typealias RowIntentWrite = RowWrite<RowIntent>

enum RowIntent: Equatable, Sendable {
    case fixo(Bool)
    case deletion

    var fixo: Bool? {
        guard case .fixo(let on) = self else { return nil }
        return on
    }
}

extension WriteIndicator {
    static func of(_ intent: RowIntentWrite?) -> WriteIndicator {
        .of(intent, subject: intent?.verb == .deletion ? .deleting : .saving)
    }
}
