import Foundation

struct APITransactionRepository: TransactionRepository {
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

        /// Not derived from the wire value: a renamed path or a new case must fail to compile.
        private static func segment(_ status: PaymentStatus) -> String {
            switch status {
            case .paid: "paid"
            case .pending: "pending"
            }
        }
    }
}
