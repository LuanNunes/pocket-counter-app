import Foundation

/// What a control shows about its pending or failed write.
struct WriteIndicator: Equatable {
    enum Remedy: Equatable {
        case retry
        case refresh
    }

    let isBusy: Bool
    let notice: PocketNotice?
    let remedy: Remedy?

    static let none = WriteIndicator(isBusy: false, notice: nil, remedy: nil)

    static func of<Target>(
        _ write: RowWrite<Target>?, subject: WriteFailureMessage.Subject
    ) -> WriteIndicator {
        guard let write else { return .none }
        switch write.phase {
        case .inFlight:
            return WriteIndicator(isBusy: true, notice: nil, remedy: nil)
        case .settled:
            return .none
        case .failed(let failure):
            guard let notice = WriteFailureMessage.message(for: failure, subject: subject) else { return .none }
            return WriteIndicator(isBusy: false, notice: notice, remedy: failure == .vanished ? .refresh : .retry)
        }
    }
}
