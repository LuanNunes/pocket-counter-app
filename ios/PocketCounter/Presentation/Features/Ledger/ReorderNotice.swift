enum ReorderNotice {
    /// Scoped to its month and kind, unlike a filter, which is only a lens.
    static func message(for failed: FailedReorder?, month: RefYearMonth, kind: TransactionType) -> PocketNotice? {
        guard let failed, failed.ref == month, failed.kind == kind else { return nil }
        return WriteFailureMessage.message(for: failed.failure, subject: .reordering)
    }
}
