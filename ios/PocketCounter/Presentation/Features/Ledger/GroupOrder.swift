/// The order of one group's rows, as the accessibility actions and the drag both need it.
struct GroupOrder: Equatable {
    let ids: [TransactionID]

    func movingUp(_ id: TransactionID) -> [TransactionID]? {
        guard let index = ids.firstIndex(of: id), index > 0 else { return nil }
        return swapped(index, index - 1)
    }

    func movingDown(_ id: TransactionID) -> [TransactionID]? {
        guard let index = ids.firstIndex(of: id), index < ids.count - 1 else { return nil }
        return swapped(index, index + 1)
    }

    private func swapped(_ a: Int, _ b: Int) -> [TransactionID] {
        var result = ids
        result.swapAt(a, b)
        return result
    }
}
