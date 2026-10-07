import Foundation

/// What a row shows about its pending or failed status write.
struct TransactionRowWrite: Equatable {
    enum Remedy: Equatable {
        case retry
        case refresh
    }

    let isBusy: Bool
    let notice: PocketNotice?
    let remedy: Remedy?

    static let none = TransactionRowWrite(isBusy: false, notice: nil, remedy: nil)

    static func of<Target>(
        _ write: RowWrite<Target>?, subject: WriteFailureMessage.Subject
    ) -> TransactionRowWrite {
        guard let write else { return .none }
        switch write.phase {
        case .inFlight:
            return TransactionRowWrite(isBusy: true, notice: nil, remedy: nil)
        case .failed(let failure):
            guard let notice = WriteFailureMessage.message(for: failure, subject: subject) else { return .none }
            return TransactionRowWrite(isBusy: false, notice: notice, remedy: failure == .vanished ? .refresh : .retry)
        }
    }

    static func of(_ intent: RowIntentWrite?) -> TransactionRowWrite {
        .of(intent, subject: intent?.verb == .deletion ? .deleting : .saving)
    }

    /// Targets of the writes that landed, to announce to VoiceOver. An entry also leaves the map
    /// when a write is dropped on `.sessionExpired`, so absence alone would announce a save that
    /// never happened: the row must actually show the target.
    static func completed(
        from old: [TransactionID: PaymentStatusWrite],
        to new: [TransactionID: PaymentStatusWrite],
        statusOf: (TransactionID) -> PaymentStatus?
    ) -> [PaymentStatus] {
        old.compactMap { id, write in
            guard new[id] == nil, let target = write.target, statusOf(id) == target else { return nil }
            return target
        }
    }
}
