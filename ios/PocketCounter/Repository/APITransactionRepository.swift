import Foundation

struct APITransactionRepository: TransactionRepository {
    private struct ReorderBody: Encodable, Sendable {
        struct Item: Encodable, Sendable {
            let id: String
            let displayOrder: Int
        }

        let items: [Item]
    }

    private struct CreateBody: Encodable, Sendable {
        /// The server requires `name` with no default (`TagDto.kt:12`); a tag with only an id is a 400.
        struct Tag: Encodable, Sendable {
            let id: String
            let name = ""
        }

        let name: String
        let amount: Decimal
        let dateDue: String
        /// Sent explicitly: the server's `datePurchase ?? dateDue` fallback is temporary (TransactionService.kt:356).
        let datePurchase: String
        let refYearMonth: Int
        let paymentMethod: String?
        let cardId: String?
        let tags: [Tag]?
        let allowDuplicate: Bool
    }

    private let client: AuthenticatedAPIClient

    init(client: AuthenticatedAPIClient) {
        self.client = client
    }

    func month(_ ref: RefYearMonth) async throws(LoadFailure) -> [HistoryItem] {
        async let incomes = outcome(Route.incomes(ref))
        async let expenses = outcome(Route.expenses(ref))
        return try ordered(await incomes.get() + expenses.get())
    }

    func range(_ span: RefYearMonthRange) async throws(LoadFailure) -> [HistoryItem] {
        try ordered(await outcome(Route.range(span)).get())
    }

    func invoiceItems(_ id: TransactionID) async throws(LoadFailure) -> [InvoiceItem] {
        try await client.load(Route.items(id)).mappedOrFailing(InvoiceItemMapper.map)
    }

    func setPaymentStatus(_ status: PaymentStatus, on id: TransactionID) async throws(WriteFailure) {
        try await client.write(Route.paymentStatus(status, of: id))
    }

    func delete(_ id: TransactionID) async throws(WriteFailure) {
        try await client.write(Route.delete(id))
    }

    func reorder(_ ids: [TransactionID]) async throws(WriteFailure) {
        try await client.write(Route.reorder(ids))
    }

    func create(_ entry: TransactionEntry) async throws(WriteFailure) {
        try await client.write(Route.create(entry))
    }

    /// `async let` erases a typed throw to `any Error`, so the failure travels as a `Result`.
    private func outcome(_ endpoint: Endpoint<[TransactionDTO]>) async -> Result<[TransactionDTO], LoadFailure> {
        do {
            return .success(try await client.load(endpoint))
        } catch {
            return .failure(error)
        }
    }

    private func ordered(_ dtos: [TransactionDTO]) throws(LoadFailure) -> [HistoryItem] {
        LedgerOrder.sorted(try dtos.mappedOrFailing(TransactionMapper.map))
    }

    enum Route {
        static func incomes(_ ref: RefYearMonth) -> Endpoint<[TransactionDTO]> {
            Endpoint(method: .get, path: "api/v1/transactions/incomes/\(ref.raw)", authentication: .bearer)
        }

        static func expenses(_ ref: RefYearMonth) -> Endpoint<[TransactionDTO]> {
            Endpoint(method: .get, path: "api/v1/transactions/expenses/\(ref.raw)", authentication: .bearer)
        }

        static func range(_ span: RefYearMonthRange) -> Endpoint<[TransactionDTO]> {
            Endpoint(
                method: .get, path: "api/v1/transactions/range/\(span.from.raw)/\(span.through.raw)",
                authentication: .bearer
            )
        }

        static func items(_ id: TransactionID) -> Endpoint<[TransactionItemDTO]> {
            Endpoint(method: .get, path: "api/v1/transactions/\(id.rawValue)/items", authentication: .bearer)
        }

        static func paymentStatus(_ status: PaymentStatus, of id: TransactionID) -> Endpoint<EmptyResponse> {
            Endpoint(
                method: .put, path: "api/v1/transactions/\(id.rawValue)/\(segment(status))", authentication: .bearer
            )
        }

        static func delete(_ id: TransactionID) -> Endpoint<EmptyResponse> {
            Endpoint(method: .delete, path: "api/v1/transactions/\(id.rawValue)", authentication: .bearer)
        }

        static func reorder(_ ids: [TransactionID]) -> Endpoint<EmptyResponse> {
            let items = ids.enumerated().map { ReorderBody.Item(id: $1.rawValue, displayOrder: $0) }
            return Endpoint(
                method: .put, path: "api/v1/transactions/reorder", authentication: .bearer, body: ReorderBody(items: items)
            )
        }

        /// The path carries the direction, so the body does not send `transactionType`.
        static func create(_ entry: TransactionEntry) -> Endpoint<EmptyResponse> {
            Endpoint(
                method: .post, path: "api/v1/transactions/\(segment(entry.type))", authentication: .bearer,
                body: CreateBody(
                    name: entry.name,
                    amount: entry.amount.amount,
                    dateDue: entry.date.iso,
                    datePurchase: entry.date.iso,
                    refYearMonth: entry.date.refYearMonth.raw,
                    paymentMethod: entry.paymentMethod?.wire,
                    cardId: entry.card?.rawValue,
                    tags: entry.tag.map { [CreateBody.Tag(id: $0.rawValue)] },
                    allowDuplicate: entry.allowDuplicate
                )
            )
        }

        private static func segment(_ type: TransactionType) -> String {
            switch type {
            case .income: "incomes"
            case .expense: "expenses"
            }
        }

        /// Not derived from the wire value: a renamed path or a new case must fail to compile.
        private static func segment(_ status: PaymentStatus) -> String {
            switch status {
            case .paid: "paid"
            case .pending: "pending"
            }
        }
    }
}
