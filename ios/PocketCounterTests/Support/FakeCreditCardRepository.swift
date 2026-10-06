import Foundation

@testable import PocketCounter

struct FakeCreditCardRepository: CreditCardRepository {
    var cardsResult: Result<[CreditCard], LoadFailure> = .success([])
    var rendezvous: Rendezvous?

    func cards() async throws(LoadFailure) -> [CreditCard] {
        await rendezvous?.arrive()
        return try cardsResult.get()
    }

    func invalidateLookups() async {}
}
