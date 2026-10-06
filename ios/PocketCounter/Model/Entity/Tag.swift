import Foundation

struct Tag: Hashable, Sendable {
    let id: TagID
    let name: String
    let kind: TransactionType
    let contextId: ContextID?
    let color: UInt32?

    init(
        id: TagID,
        name: String,
        kind: TransactionType,
        contextId: ContextID? = nil,
        color: UInt32? = nil
    ) {
        self.id = id
        self.name = name
        self.kind = kind
        self.contextId = contextId
        self.color = color
    }
}

struct TagContext: Hashable, Sendable {
    let id: ContextID
    let name: String
    let color: UInt32?
}
