import Foundation

@testable import PocketCounter

struct FakeSentenceReadingRepository: SentenceReadingRepository {
    var result: Result<SentenceReading, ReadingFailure> = .success(.fixture())
    var rendezvous: Rendezvous?

    func reading(of text: SentenceText, on day: CalendarDay) async throws(ReadingFailure) -> SentenceReading {
        await rendezvous?.arrive()
        return try result.get()
    }
}
