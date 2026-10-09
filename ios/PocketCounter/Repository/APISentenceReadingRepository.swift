import Foundation

/// Holds no cache: a sentence is read once and never stored.
struct APISentenceReadingRepository: SentenceReadingRepository {
    enum Route {
        static func reading(of text: SentenceText, on day: CalendarDay) -> Endpoint<TransactionRawResponseDTO> {
            Endpoint(
                method: .post, path: "api/v1/transactions/raw", authentication: .bearer,
                body: TransactionRawRequestDTO(text: text.value, referenceDate: day.iso)
            )
        }
    }

    private let client: AuthenticatedAPIClient

    init(client: AuthenticatedAPIClient) {
        self.client = client
    }

    func reading(of text: SentenceText, on day: CalendarDay) async throws(ReadingFailure) -> SentenceReading {
        let dto = try await client.read(Route.reading(of: text, on: day))
        do {
            return try SentenceReadingMapper.map(dto)
        } catch {
            throw ReadingFailure(error)
        }
    }
}
