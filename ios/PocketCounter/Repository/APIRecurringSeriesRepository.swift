import Foundation

struct APIRecurringSeriesRepository: RecurringSeriesRepository {
    private struct CreateBody: Encodable, Sendable {
        let name: String
        let transactionType: String
        let recurrenceDay: Int
    }

    private let client: AuthenticatedAPIClient

    init(client: AuthenticatedAPIClient) {
        self.client = client
    }

    func create(_ draft: RecurringSeriesDraft) async throws(WriteFailure) -> RecurringSeries {
        let dto = try await client.write(Route.create(draft))
        return try WriteFailure.mapping(dto, with: RecurringSeriesMapper.map)
    }

    func link(_ id: TransactionID, to series: SeriesID) async throws(WriteFailure) {
        try await client.write(Route.link(id, to: series))
    }

    func unlink(_ id: TransactionID, from series: SeriesID) async throws(WriteFailure) {
        try await client.write(Route.unlink(id, from: series))
    }

    enum Route {
        // No trailing slash: the backend maps `@PostMapping("")`.
        static func create(_ draft: RecurringSeriesDraft) -> Endpoint<RecurringSeriesDTO> {
            Endpoint(
                method: .post, path: "api/v1/recurring-series", authentication: .bearer,
                body: CreateBody(name: draft.name, transactionType: draft.type.wire, recurrenceDay: draft.recurrenceDay)
            )
        }

        static func link(_ id: TransactionID, to series: SeriesID) -> Endpoint<EmptyResponse> {
            Endpoint(method: .post, path: member(series, id), authentication: .bearer)
        }

        static func unlink(_ id: TransactionID, from series: SeriesID) -> Endpoint<EmptyResponse> {
            Endpoint(method: .delete, path: member(series, id), authentication: .bearer)
        }

        private static func member(_ series: SeriesID, _ id: TransactionID) -> String {
            "api/v1/recurring-series/\(series.rawValue)/transactions/\(id.rawValue)"
        }
    }
}
