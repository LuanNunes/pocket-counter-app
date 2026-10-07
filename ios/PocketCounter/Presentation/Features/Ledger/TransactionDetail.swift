import Foundation

struct TransactionDetail: Equatable {
    /// As displayed, with the status overlay applied: the verbs take it as it stands.
    let item: HistoryItem
    let title: String
    let dateLabel: String
    let kind: TransactionType
    let amount: Decimal
    let isPaid: Bool
    /// What was asked for while a toggle is in flight, else what the ledger says.
    let isFixo: Bool
    let payLabel: String?
    let tags: [TransactionRowContent.TagChip]
    let statusWrite: TransactionRowWrite
    let fixoWrite: TransactionRowWrite
    let deleteWrite: TransactionRowWrite

    var isBusy: Bool { statusWrite.isBusy || fixoWrite.isBusy || deleteWrite.isBusy }
}

enum TransactionDetailProjection: Equatable {
    case showing(TransactionDetail)
    case gone

    /// The ref mismatch is reachable (Início and Transações share one model). Not holding the row
    /// is too. A matching ref with no committed ledger is not, and shares the one branch.
    static func of(_ target: DetailTarget, in state: MonthLedgerModel.State) -> TransactionDetailProjection {
        guard target.ref == state.month,
            let ledger = state.load.value,
            let item = ledger.items.first(where: { $0.id == target.id })
        else { return .gone }
        let lookups = ledger.lookups
        let intent = state.intents[item.id]
        let intentWrite = TransactionRowWrite.of(intent)
        let isDeletion = intent?.verb == .deletion
        return .showing(TransactionDetail(
            item: item,
            title: item.displayTitle(),
            dateLabel: PocketFormat.dayLabel(item.date),
            kind: item.type,
            amount: item.amount.amount,
            isPaid: item.statusPayment == .paid,
            isFixo: intent?.target?.fixo ?? item.isFixo,
            payLabel: TransactionRowContent.payLabel(item, lookups: lookups),
            tags: item.effectiveTagIds(inheriting: []).map { TransactionRowContent.chip(for: $0, lookups: lookups) },
            statusWrite: .of(state.writes[item.id], subject: .saving),
            fixoWrite: isDeletion ? .none : intentWrite,
            deleteWrite: isDeletion ? intentWrite : .none
        ))
    }
}
