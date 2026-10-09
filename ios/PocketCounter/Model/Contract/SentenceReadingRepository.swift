import Foundation

protocol SentenceReadingRepository: Sendable {
    /// `day` is the device's local date: it is what "hoje" means, and the server's UTC would file a late entry on the wrong day.
    func reading(of text: SentenceText, on day: CalendarDay) async throws(ReadingFailure) -> SentenceReading
}
