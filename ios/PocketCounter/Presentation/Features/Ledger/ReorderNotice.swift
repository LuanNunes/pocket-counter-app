enum ReorderNotice {
    static func message(for failure: WriteFailure?) -> PocketNotice? {
        guard let failure else { return nil }
        return WriteFailureMessage.message(for: failure, subject: .reordering)
    }
}
