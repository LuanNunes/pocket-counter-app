import Foundation

struct APIRecurringTransactionRepository: RecurringTransactionRepository {
    private struct CreateBody: Encodable, Sendable {
        let name: String
        let transactionType: String
    }

    private let client: AuthenticatedAPIClient

    init(client: AuthenticatedAPIClient) {
        self.client = client
    }

    func create(_ draft: RecurringTransactionDraft) async throws(WriteFailure) -> RecurringTransaction {
        let dto = try await client.write(Route.create(draft))
        return try WriteFailure.mapping(dto, with: RecurringTransactionMapper.map)
    }

    func link(_ id: TransactionID, to recurring: RecurringTransactionID) async throws(WriteFailure) {
        try await client.write(Route.link(id, to: recurring))
    }

    func unlink(_ id: TransactionID, from recurring: RecurringTransactionID) async throws(WriteFailure) {
        try await client.write(Route.unlink(id, from: recurring))
    }

    enum Route {
        // No trailing slash: the backend maps `@PostMapping("")`.
        static func create(_ draft: RecurringTransactionDraft) -> Endpoint<RecurringTransactionDTO> {
            Endpoint(
                method: .post, path: "api/v1/recurring-transactions", authentication: .bearer,
                body: CreateBody(name: draft.name, transactionType: draft.type.wire)
            )
        }

        static func link(_ id: TransactionID, to recurring: RecurringTransactionID) -> Endpoint<EmptyResponse> {
            Endpoint(method: .post, path: member(recurring, id), authentication: .bearer)
        }

        static func unlink(_ id: TransactionID, from recurring: RecurringTransactionID) -> Endpoint<EmptyResponse> {
            Endpoint(method: .delete, path: member(recurring, id), authentication: .bearer)
        }

        private static func member(_ recurring: RecurringTransactionID, _ id: TransactionID) -> String {
            "api/v1/recurring-transactions/\(recurring.rawValue)/transactions/\(id.rawValue)"
        }
    }
}
