/// A reorder belongs to no row, so it is not a `RowWrite`. Only its failure is kept: the list
/// already shows the new order, so there is no in-flight state to render.
struct FailedReorder: Equatable, Sendable {
    let ref: RefYearMonth
    let kind: TransactionType
    let failure: WriteFailure
}
