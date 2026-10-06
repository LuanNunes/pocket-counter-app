import Foundation

protocol CreditCardRepository: LookupCaching {
    func cards() async throws(LoadFailure) -> [CreditCard]
}
