struct DetailTarget: Equatable, Sendable {
    let id: TransactionID
    let ref: RefYearMonth
}

/// What the Transações screen remembers between renders. Collapse is keyed by group identity and
/// outlives nothing: a different kind, mode or month is a different set of groups.
struct TransactionsViewState: Equatable {
    private(set) var kind: TransactionType = .expense
    private(set) var mode: LedgerGroupMode = .lista
    private(set) var query = ""
    private(set) var onlyFixos = false
    private(set) var collapsed: Set<LedgerGroupIdentity> = []
    private(set) var detail: DetailTarget?

    init(
        kind: TransactionType = .expense, mode: LedgerGroupMode = .lista, query: String = "", onlyFixos: Bool = false
    ) {
        self.kind = kind
        self.mode = mode
        self.query = query
        self.onlyFixos = onlyFixos
    }

    var filter: LedgerFilter {
        LedgerFilter(kind: kind, query: query, onlyFixos: onlyFixos)
    }

    mutating func set(query: String) {
        self.query = query
    }

    mutating func toggleOnlyFixos() {
        onlyFixos.toggle()
    }

    mutating func select(kind: TransactionType) {
        guard kind != self.kind else { return }
        self.kind = kind
        collapsed = []
    }

    mutating func select(mode: LedgerGroupMode) {
        guard mode != self.mode else { return }
        self.mode = mode
        collapsed = []
    }

    mutating func monthChanged() {
        collapsed = []
        detail = nil
    }

    mutating func openDetail(_ target: DetailTarget) {
        detail = target
    }

    mutating func closeDetail() {
        detail = nil
    }

    mutating func toggle(_ identity: LedgerGroupIdentity) {
        guard collapsed.remove(identity) == nil else { return }
        collapsed.insert(identity)
    }
}

enum TransactionsAction: Equatable {
    case selectKind(TransactionType)
    case selectMode(LedgerGroupMode)
    case setQuery(String)
    case toggleOnlyFixos
    case toggleGroup(LedgerGroupIdentity)
    case openDetail(DetailTarget)
    case closeDetail
}
