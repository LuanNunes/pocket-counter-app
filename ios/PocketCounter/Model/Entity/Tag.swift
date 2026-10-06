import Foundation

struct Tag: Hashable, Sendable {
    let id: TagID
    let name: String
    let kind: TransactionType
    let contextId: ContextID?
    let color: UInt32?
    let seriesId: String?

    init(
        id: TagID,
        name: String,
        kind: TransactionType,
        contextId: ContextID? = nil,
        color: UInt32? = nil,
        seriesId: String? = nil
    ) {
        self.id = id
        self.name = name
        self.kind = kind
        self.contextId = contextId
        self.color = color
        self.seriesId = seriesId
    }
}

struct TagContext: Hashable, Sendable {
    let id: ContextID
    let name: String
    let color: UInt32
}
